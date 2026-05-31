package com.hss.scalecart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_number")
    private Integer partition;

    @Column(name = "kafka_offset")
    private Long offset;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private boolean resolved;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}