package com.hss.scalecart.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeadLetterEventResponse {
    private UUID id;
    private String topic;
    private Integer partition;
    private Long offset;
    private String payload;
    private String errorReason;
    private int retryCount;
    private boolean resolved;
    private LocalDateTime createdAt;
}