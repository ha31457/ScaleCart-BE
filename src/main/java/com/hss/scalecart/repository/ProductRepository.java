package com.hss.scalecart.repository;

import com.hss.scalecart.entity.Product;
import com.hss.scalecart.enums.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Keyset pagination query — the core of scalable product listing.
    // Instead of OFFSET (which scans all skipped rows), we anchor to the
    // last seen ID. With the composite index on (category, status, created_at),
    // this is O(log n) regardless of how deep into the list you are.
    @Query("""
        SELECT p FROM Product p
        WHERE p.status = :status
        AND (:category IS NULL OR p.category = :category)
        AND (:lastSeenId IS NULL OR p.id > :lastSeenId)
        ORDER BY p.id ASC
        """)
    List<Product> findProductsWithKeyset(
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("lastSeenId") UUID lastSeenId,
            Pageable pageable
    );

    Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

    @Query("""
        SELECT p FROM Product p
        WHERE p.sellerId = :sellerId
        AND (:lastSeenId IS NULL OR p.id > :lastSeenId)
        ORDER BY p.id ASC
        """)
    List<Product> findBySellerIdWithKeyset(
            @Param("sellerId") UUID sellerId,
            @Param("lastSeenId") UUID lastSeenId,
            Pageable pageable
    );
}