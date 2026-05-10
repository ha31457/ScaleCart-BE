package com.hss.scalecart.dto.response;

import com.hss.scalecart.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private UUID id;
    private UUID customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String idempotencyKey;
    private String failureReason;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}