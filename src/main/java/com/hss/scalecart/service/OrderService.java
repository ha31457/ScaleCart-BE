package com.hss.scalecart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hss.scalecart.dto.request.CreateOrderRequest;
import com.hss.scalecart.dto.request.OrderItemRequest;
import com.hss.scalecart.dto.response.OrderItemResponse;
import com.hss.scalecart.dto.response.OrderResponse;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.entity.Order;
import com.hss.scalecart.entity.OrderItem;
import com.hss.scalecart.entity.OutboxEvent;
import com.hss.scalecart.entity.Product;
import com.hss.scalecart.enums.OrderStatus;
import com.hss.scalecart.enums.ProductStatus;
import com.hss.scalecart.repository.OrderRepository;
import com.hss.scalecart.repository.OutboxEventRepository;
import com.hss.scalecart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String IDEMPOTENCY_CACHE_PREFIX = "idempotency:order:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int PAGE_SIZE = 20;

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(UUID customerId, String idempotencyKey, CreateOrderRequest request) {
        // 1. Idempotency check
        String cacheKey = IDEMPOTENCY_CACHE_PREFIX + idempotencyKey;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Duplicate order request detected for idempotency key: {}", idempotencyKey);
                return (OrderResponse) cached;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check, proceeding without cache: {}", e.getMessage());
        }

        // 2. Validate all products exist and are ACTIVE
        List<UUID> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList();

        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new IllegalArgumentException("One or more products not found");
        }

        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Product product : products) {
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new IllegalArgumentException("Product " + product.getId() + " is not available for purchase");
            }
        }

        // 3. Reserve stock for each item
        List<UUID> reservedProducts = new ArrayList<>();
        try {
            for (OrderItemRequest item : request.getItems()) {
                inventoryService.reserveStock(item.getProductId(), item.getQuantity());
                reservedProducts.add(item.getProductId());
            }
        } catch (Exception e) {
            // Compensate — release already reserved stock
            for (UUID productId : reservedProducts) {
                int qty = request.getItems().stream()
                        .filter(i -> i.getProductId().equals(productId))
                        .findFirst()
                        .map(OrderItemRequest::getQuantity)
                        .orElse(0);
                inventoryService.releaseStock(productId, qty);
            }
            throw new IllegalStateException("Stock reservation failed: " + e.getMessage(), e);
        }

        // 4. Build order items + total
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productMap.get(itemRequest.getProductId());
            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .build();
            orderItems.add(orderItem);
        }

        // 5. Persist Order + OutboxEvent in single transaction
        Order order = Order.builder()
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .idempotencyKey(idempotencyKey)
                .shippingAddress(request.getShippingAddress())
                .build();
        order.setItems(orderItems);
        orderItems.forEach(item -> item.setOrder(order));

        Order savedOrder = orderRepository.save(order);

        String payload = buildOutboxPayload(savedOrder);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(savedOrder.getId())
                .eventType("ORDER_PLACED")
                .payload(payload)
                .published(false)
                .build();
        outboxEventRepository.save(outboxEvent);

        // 6. Build response + cache under idempotency key
        OrderResponse response = toResponse(savedOrder, productMap);
        try {
            redisTemplate.opsForValue().set(cacheKey, response, IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable, idempotency result not cached: {}", e.getMessage());
        }

        log.info("Order placed successfully: orderId={}, customerId={}", savedOrder.getId(), customerId);
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Access denied to order: " + orderId);
        }

        List<UUID> productIds = order.getItems().stream()
                .map(OrderItem::getProductId).toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return toResponse(order, productMap);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrderHistory(UUID customerId, UUID cursor) {
        List<Order> orders = orderRepository.findByCustomerIdKeyset(
                customerId, cursor, PageRequest.of(0, PAGE_SIZE + 1)
        );

        boolean hasNext = orders.size() > PAGE_SIZE;
        List<Order> page = hasNext ? orders.subList(0, PAGE_SIZE) : orders;

        List<UUID> productIds = page.stream()
                .flatMap(o -> o.getItems().stream())
                .map(OrderItem::getProductId)
                .distinct().toList();

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderResponse> responses = page.stream()
                .map(o -> toResponse(o, productMap))
                .toList();

        UUID nextCursorUUID = hasNext ? page.get(page.size() - 1).getId() : null;

        return PagedResponse.<OrderResponse>builder()
                .items(responses)
                .pageSize(page.size())
                .hasMore(hasNext)
                .nextCursor(nextCursorUUID)
                .build();
    }

    private OrderResponse toResponse(Order order, Map<UUID, Product> productMap) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    return OrderItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(product != null ? product.getName() : "Unknown")
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .build();
                })
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .idempotencyKey(order.getIdempotencyKey())
                .failureReason(order.getFailureReason())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private String buildOutboxPayload(Order order) {
        try {
            ObjectMapper plainMapper = new ObjectMapper();
            Map<String, String> payload = new HashMap<>();
            payload.put("orderId", order.getId().toString());
            payload.put("customerId", order.getCustomerId().toString());
            payload.put("status", order.getStatus().name());
            payload.put("totalAmount", order.getTotalAmount().toString());
            return plainMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}