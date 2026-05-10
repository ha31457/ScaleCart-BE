package com.hss.scalecart.repository;

import com.hss.scalecart.entity.Order;
import com.hss.scalecart.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT o FROM Order o
            WHERE o.customerId = :customerId
            AND (:cursor IS NULL OR o.createdAt < (SELECT o2.createdAt FROM Order o2 WHERE o2.id = :cursor))
            ORDER BY o.createdAt DESC
            """)
    List<Order> findByCustomerIdKeyset(
            @Param("customerId") UUID customerId,
            @Param("cursor") UUID cursor,
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsByIdempotencyKey(String idempotencyKey);
}