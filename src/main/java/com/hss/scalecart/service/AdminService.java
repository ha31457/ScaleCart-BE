package com.hss.scalecart.service;

import com.hss.scalecart.dto.response.DeadLetterEventResponse;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.entity.DeadLetterEvent;
import com.hss.scalecart.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AdminService {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    // Manual injection for @Qualifier — same pattern as OutboxPoller/PaymentService
    public AdminService(DeadLetterEventRepository deadLetterEventRepository,
                        @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    public PagedResponse<DeadLetterEventResponse> getDlqEvents(String cursorStr, int size) {
        LocalDateTime cursor = cursorStr != null ? LocalDateTime.parse(cursorStr) : null;
        List<DeadLetterEvent> events = (cursor == null)
                ? deadLetterEventRepository.findByResolvedFalseOrderByCreatedAtDesc(PageRequest.of(0, size + 1))
                : deadLetterEventRepository.findUnresolvedBefore(cursor, PageRequest.of(0, size + 1));
        boolean hasMore = events.size() > size;
        List<DeadLetterEvent> page = hasMore ? events.subList(0, size) : events;

        String nextCursor = hasMore
                ? page.get(page.size() - 1).getCreatedAt().toString()
                : null;

        List<DeadLetterEventResponse> items = page.stream().map(this::toResponse).toList();
        return new PagedResponse<>(items, size, hasMore, nextCursor);
    }

    @Transactional
    public void requeueEvent(UUID id) {
        DeadLetterEvent event = deadLetterEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ event not found: " + id));

        if (event.isResolved()) {
            throw new IllegalStateException("Event already resolved: " + id);
        }

        // Strip .dlq suffix to get original topic
        String originalTopic = event.getTopic().replace(".dlq", "");
        log.info("Requeueing DLQ event {} to topic {}", id, originalTopic);

        stringKafkaTemplate.send(originalTopic, event.getPayload());
        event.setResolved(true);
        deadLetterEventRepository.save(event);
    }

    private DeadLetterEventResponse toResponse(DeadLetterEvent e) {
        return DeadLetterEventResponse.builder()
                .id(e.getId())
                .topic(e.getTopic())
                .partition(e.getPartition())
                .offset(e.getOffset())
                .payload(e.getPayload())
                .errorReason(e.getErrorReason())
                .retryCount(e.getRetryCount())
                .resolved(e.isResolved())
                .createdAt(e.getCreatedAt())
                .build();
    }
}