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
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper plainMapper = new ObjectMapper();

    public OrderEventConsumer(OrderRepository orderRepository,
                              OutboxEventRepository outboxEventRepository,
                              InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = KafkaConfig.ORDER_PLACED_TOPIC, groupId = "scalecart-group")
    @Transactional
    public void handleOrderPlaced(String payload) {
        log.info("ORDER_PLACED event received: {}", payload);
        try {
            JsonNode node = plainMapper.readTree(payload);
            UUID orderId = UUID.fromString(node.get("orderId").asText());

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            if (order.getStatus() != OrderStatus.PENDING) {
                log.warn("Order {} already processed, status={}", orderId, order.getStatus());
                return;
            }

            // Order acknowledged — stays PENDING until payment is made
            log.info("Order {} acknowledged, awaiting payment", orderId);

        } catch (Exception e) {
            log.error("Failed to process ORDER_PLACED event: {}", e.getMessage(), e);
        }
    }
}