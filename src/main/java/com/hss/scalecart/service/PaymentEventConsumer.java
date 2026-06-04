package com.hss.scalecart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hss.scalecart.config.KafkaConfig;
import com.hss.scalecart.entity.Order;
import com.hss.scalecart.entity.OrderItem;
import com.hss.scalecart.entity.OutboxEvent;
import com.hss.scalecart.enums.OrderStatus;
import com.hss.scalecart.repository.OrderRepository;
import com.hss.scalecart.repository.OutboxEventRepository;
import com.hss.scalecart.util.KafkaHeaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper plainMapper = new ObjectMapper();

    @KafkaListener(topics = "payment.events", groupId = "scalecart-group")
    @Transactional
    public void handlePaymentEvent(ConsumerRecord<String, String> record) throws Exception {
        String traceId = KafkaHeaderUtils.extractTraceId(record);
        try {
            if (traceId != null) MDC.put("traceId", traceId);

            String payload = record.value();

            JsonNode node = plainMapper.readTree(payload);
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            String customerId = node.get("customerId").asText();
            String status = node.get("status").asText();

            MDC.put("orderId", orderId.toString());
            MDC.put("customerId", customerId);
            log.info("PAYMENT event received: {}", payload);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            if ("SUCCESS".equals(status)) {
                handlePaymentSuccess(order);
            } else if ("FAILED".equals(status)) {
                String failureReason = node.has("failureReason")
                        ? node.get("failureReason").asText()
                        : "Payment failed";
                handlePaymentFailure(order, failureReason);
            } else {
                log.warn("Unknown payment status: {}", status);
            }

        } finally {
            MDC.remove("traceId");
            MDC.remove("orderId");
            MDC.remove("customerId");
        }
    }

    private void handlePaymentSuccess(Order order) throws Exception {
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} already processed, status={}", order.getId(), order.getStatus());
            return;
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        String confirmPayload = plainMapper.writeValueAsString(Map.of(
                "orderId", order.getId().toString(),
                "customerId", order.getCustomerId().toString(),
                "status", OrderStatus.CONFIRMED.name()
        ));

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType("ORDER_CONFIRMED")
                .payload(confirmPayload)
                .published(false)
                .build());

        log.info("Order {} CONFIRMED after payment success", order.getId());
    }

    private void handlePaymentFailure(Order order, String failureReason) throws Exception {
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} already processed, status={}", order.getId(), order.getStatus());
            return;
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(failureReason);
        orderRepository.save(order);

        String failedPayload = plainMapper.writeValueAsString(Map.of(
                "orderId", order.getId().toString(),
                "customerId", order.getCustomerId().toString(),
                "status", OrderStatus.FAILED.name(),
                "failureReason", failureReason
        ));

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType("ORDER_FAILED")
                .payload(failedPayload)
                .published(false)
                .build());

        log.info("Order {} FAILED after payment failure: {}", order.getId(), failureReason);
    }
}