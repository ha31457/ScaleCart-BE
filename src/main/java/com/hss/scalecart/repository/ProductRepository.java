package com.hss.scalecart.repository;

import com.hss.scalecart.entity.Product;
import com.hss.scalecart.enums.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

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

    // First page — no cursor
    @Query("""
        SELECT p FROM Product p
        WHERE p.status = :status
        AND (:category IS NULL OR p.category = :category)
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    List<Product> searchProducts(
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    // Subsequent pages — with cursor
    @Query("""
        SELECT p FROM Product p
        WHERE p.status = :status
        AND (:category IS NULL OR p.category = :category)
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND (p.createdAt < :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    List<Product> searchProductsWithCursor(
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}