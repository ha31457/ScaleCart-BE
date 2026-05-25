package com.hss.scalecart.controller;

import com.hss.scalecart.dto.request.PaymentRequest;
import com.hss.scalecart.dto.response.ApiResponse;
import com.hss.scalecart.dto.response.PaymentResponse;
import com.hss.scalecart.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(
                UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment processed"));
    }
}