package com.hss.scalecart.repository;

import com.hss.scalecart.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    // Standard read — used for display (available stock on product page)
    Optional<Inventory> findByProductId(UUID productId);

    // Optimistic lock read — used during order placement.
    // @Version on the entity handles conflict detection automatically.
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(
            @Param("productId") UUID productId
    );
}