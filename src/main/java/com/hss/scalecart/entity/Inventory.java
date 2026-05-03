package com.hss.scalecart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    // Inventory's PK is the product_id itself — one-to-one relationship.
    // No surrogate key needed. No BaseEntity either — inventory has
    // no created_at, only updated_at which we manage manually.
    @Id
    @Column(name = "product_id", updatable = false, nullable = false)
    private UUID productId;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 0;

    // reserved = stock locked by PENDING orders, not yet fulfilled.
    // available stock = quantity - reserved.
    // We NEVER show raw quantity to customers — always quantity - reserved.
    @Column(nullable = false)
    @Builder.Default
    private int reserved = 0;

    // THE critical annotation. When two transactions update the same
    // inventory row concurrently, the second one will get an
    // OptimisticLockException — we catch and retry. Zero DB locks held.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    // Business logic lives on the entity, not in a service.
    // This is the "rich domain model" pattern.
    public int getAvailableQuantity() {
        return this.quantity - this.reserved;
    }

    public boolean hasAvailableStock(int requested) {
        return getAvailableQuantity() >= requested;
    }
}