package com.hss.scalecart.service;

import com.hss.scalecart.config.KafkaConfig;
import com.hss.scalecart.entity.OutboxEvent;
import com.hss.scalecart.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload());
                event.setPublished(true);
                outboxEventRepository.save(event);
                log.info("Published outbox event: type={}, aggregateId={}", event.getEventType(), event.getAggregateId());
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
            default                 -> KafkaConfig.ORDER_LIFECYCLE_TOPIC;
        };
    }
}