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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @KafkaListener(topics = KafkaConfig.PAYMENT_EVENTS_TOPIC, groupId = "scalecart-group")
    @Transactional
    public void handlePaymentEvent(String payload) {
        log.info("PAYMENT event received: {}", payload);
        try {
            JsonNode node = plainMapper.readTree(payload);
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            String status = node.get("status").asText();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            if (status.equals("SUCCESS")) {
                handlePaymentSuccess(order);
            } else {
                String failureReason = node.has("failureReason")
                        ? node.get("failureReason").asText()
                        : "Payment failed";
                handlePaymentFailure(order, failureReason);
            }

        } catch (Exception e) {
            log.error("Failed to process PAYMENT event: {}", e.getMessage(), e);
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