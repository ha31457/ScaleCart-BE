package com.hss.scalecart.controller;

import com.hss.scalecart.dto.request.CreateOrderRequest;
import com.hss.scalecart.dto.response.ApiResponse;
import com.hss.scalecart.dto.response.OrderResponse;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.placeOrder(
                UUID.fromString(userId), idempotencyKey, request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Order Placed Successfully"));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable UUID orderId,
            @RequestAttribute("userId") String userId
    ) {
        OrderResponse response = orderService.getOrder(orderId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response, "Here are the order details"));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrderHistory(
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) UUID cursor
    ) {
        PagedResponse<OrderResponse> response = orderService.getOrderHistory(
                UUID.fromString(userId), cursor
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Here is the order history"));
    }
}