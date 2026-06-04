package com.hss.scalecart.service;

import com.hss.scalecart.config.KafkaConfig;
import com.hss.scalecart.entity.OutboxEvent;
import com.hss.scalecart.repository.OutboxEventRepository;
import com.hss.scalecart.util.KafkaHeaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> unpublished = outboxEventRepository.findUnpublishedEvents();

        if (unpublished.isEmpty()) return;

        log.info("OutboxPoller: found {} unpublished events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                String topic = resolveTopic(event.getEventType());
                // In OutboxPoller, replace the send call:
                String traceId = UUID.randomUUID().toString().replace("-", "");
                ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(
                        topic, null, event.getAggregateId().toString(), event.getPayload());
                kafkaRecord.headers().add(KafkaHeaderUtils.TRACE_ID_HEADER,
                        traceId.getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(kafkaRecord);
                log.info("Published outbox event: type={}, aggregateId={}, traceId={}",
                        event.getEventType(), event.getAggregateId(), traceId);
                outboxEventRepository.save(event);
                event.setPublished(true);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ORDER_PLACED"     -> KafkaConfig.ORDER_PLACED_TOPIC;
            case "ORDER_CONFIRMED"  -> KafkaConfig.ORDER_LIFECYCLE_TOPIC;
            case "INVENTORY_UPDATE" -> KafkaConfig.INVENTORY_EVENTS_TOPIC;
            case "ORDER_FAILED" -> KafkaConfig.ORDER_LIFECYCLE_TOPIC;
            default                 -> KafkaConfig.ORDER_LIFECYCLE_TOPIC;
        };
    }
}