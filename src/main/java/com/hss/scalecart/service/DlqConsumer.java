package com.hss.scalecart.service;

import com.hss.scalecart.entity.DeadLetterEvent;
import com.hss.scalecart.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DlqConsumer {

    private final DeadLetterEventRepository deadLetterEventRepository;

    @KafkaListener(topics = {"order.placed.dlq", "payment.events.dlq"}, groupId = "scalecart-dlq-group")
    public void consume(ConsumerRecord<String, String> record) {
        log.error("DLQ message received — topic: {}, partition: {}, offset: {}",
                record.topic(), record.partition(), record.offset());

        String errorReason = extractErrorReason(record);

        DeadLetterEvent event = DeadLetterEvent.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .payload(record.value() != null ? record.value().toString() : "null")
                .errorReason(errorReason)
                .retryCount(3)
                .resolved(false)
                .build();

        deadLetterEventRepository.save(event);
    }

    private String extractErrorReason(ConsumerRecord<String, String> record) {
        // Spring Kafka writes the exception message into a header
        org.apache.kafka.common.header.Header header =
                record.headers().lastHeader("kafka_dlt-exception-message");
        if (header != null) {
            return new String(header.value());
        }
        return "Unknown error";
    }
}