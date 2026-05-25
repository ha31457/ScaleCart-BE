package com.hss.scalecart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hss.scalecart.config.KafkaConfig;
import com.hss.scalecart.dto.request.PaymentRequest;
import com.hss.scalecart.dto.response.PaymentResponse;
import com.hss.scalecart.entity.Order;
import com.hss.scalecart.entity.Payment;
import com.hss.scalecart.enums.OrderStatus;
import com.hss.scalecart.enums.PaymentStatus;
import com.hss.scalecart.repository.OrderRepository;
import com.hss.scalecart.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper plainMapper = new ObjectMapper();

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public PaymentResponse processPayment(UUID customerId, PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + request.getOrderId()));

        if (!order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Access denied to order: " + request.getOrderId());
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Order is not in PENDING state. Current status: " + order.getStatus());
        }

        // Check if payment already exists for this order
        paymentRepository.findByOrderId(order.getId()).ifPresent(existing -> {
            throw new IllegalStateException("Payment already initiated for order: " + order.getId());
        });

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .customerId(customerId)
                .amount(order.getTotalAmount())
                .build();

        if (request.getSimulateSuccess()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            publishPaymentEvent(order.getId(), customerId, PaymentStatus.SUCCESS, null);
            log.info("Payment SUCCESS for orderId={}", order.getId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment declined by gateway");
            paymentRepository.save(payment);
            publishPaymentEvent(order.getId(), customerId, PaymentStatus.FAILED, "Payment declined by gateway");
            log.info("Payment FAILED for orderId={}", order.getId());
        }

        return toResponse(payment);
    }

    private void publishPaymentEvent(UUID orderId, UUID customerId,
                                     PaymentStatus status, String failureReason) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("orderId", orderId.toString());
            payload.put("customerId", customerId.toString());
            payload.put("status", status.name());
            if (failureReason != null) {
                payload.put("failureReason", failureReason);
            }
            String json = plainMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaConfig.PAYMENT_EVENTS_TOPIC, orderId.toString(), json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish payment event", e);
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}