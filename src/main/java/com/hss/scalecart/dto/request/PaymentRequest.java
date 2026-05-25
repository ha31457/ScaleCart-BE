package com.hss.scalecart.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    // true = payment succeeds, false = payment fails
    // In production this flag is replaced by actual payment gateway response
    @NotNull(message = "Payment success flag is required")
    private Boolean simulateSuccess;
}