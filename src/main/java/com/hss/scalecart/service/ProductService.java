package com.hss.scalecart.service;

import com.hss.scalecart.dto.request.CreateProductRequest;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.dto.response.ProductResponse;
import com.hss.scalecart.entity.Inventory;
import com.hss.scalecart.entity.Product;
import com.hss.scalecart.enums.ProductStatus;
import com.hss.scalecart.repository.InventoryRepository;
import com.hss.scalecart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hss.scalecart.dto.request.ProductSearchRequest;
import com.hss.scalecart.util.CursorUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID sellerId) {
        Product product = Product.builder()
                .sellerId(sellerId)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .status(ProductStatus.ACTIVE)
                .build();

        product = productRepository.save(product);

        // Create inventory record atomically with the product.
        // Both writes are in the same transaction — if either fails, both roll back.
        Inventory inventory = Inventory.builder()
                .productId(product.getId())
                .quantity(request.getInitialStock())
                .reserved(0)
                // version intentionally omitted — JPA sets it to 0 on first insert
                .build();

        inventoryRepository.save(inventory);

        log.info("Product created: {} by seller: {}", product.getId(), sellerId);
        return toResponse(product, inventory);
    }

    // @Cacheable: on first call, executes method and stores result in Redis.
    // On subsequent calls with same key, returns Redis value — DB never touched.
    // Cache key = "products::{id}" — TTL is 5 minutes (set in RedisConfig)
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        Product product = productRepository.findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found: " + id));

        Inventory inventory = inventoryRepository.findByProductId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inventory not found for product: " + id));

        log.debug("Cache miss — fetching product {} from DB", id);
        return toResponse(product, inventory);
    }

    // @CacheEvict: when a product is updated, immediately invalidate its cache entry.
    // Next read will be a cache miss, forcing a fresh DB fetch.
    // This is the "write-invalidate" pattern — simpler and safer than write-through.
    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public ProductResponse updateProductStatus(UUID id, ProductStatus status,
                                               UUID sellerId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found: " + id));

        if (!product.getSellerId().equals(sellerId)) {
            throw new IllegalArgumentException("Not authorized to update this product");
        }

        product.setStatus(status);
        product = productRepository.save(product);

        Inventory inventory = inventoryRepository.findByProductId(id).orElse(null);
        log.info("Product {} status updated to {} — cache evicted", id, status);
        return toResponse(product, inventory);
    }

    // Keyset pagination — no @Cacheable here intentionally.
    // List results are too dynamic (new products added constantly) to cache reliably.
    // Individual product caching (above) is where we get the most cache value.
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> listProducts(String category,
                                                       UUID lastSeenId,
                                                       int pageSize) {
        // Fetch one extra to determine if there are more pages
        int fetchSize = pageSize + 1;

        List<Product> products = productRepository.findProductsWithKeyset(
                ProductStatus.ACTIVE,
                category,
                lastSeenId,
                PageRequest.of(0, fetchSize)
        );

        boolean hasMore = products.size() > pageSize;
        if (hasMore) {
            products = products.subList(0, pageSize);
        }

        List<ProductResponse> responses = products.stream()
                .map(p -> {
                    Inventory inv = inventoryRepository
                            .findByProductId(p.getId()).orElse(null);
                    return toResponse(p, inv);
                })
                .toList();

        String nextCursor = hasMore
                ? String.valueOf(products.get(products.size() - 1).getId())
                : null;

        return PagedResponse.<ProductResponse>builder()
                .items(responses)
                .pageSize(pageSize)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .build();
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        int availableStock = inventory != null
                ? inventory.getAvailableQuantity()
                : 0;

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .status(product.getStatus())
                .availableStock(availableStock)
                .sellerId(product.getSellerId())
                .createdAt(product.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> searchProducts(ProductSearchRequest request) {
        int size = request.getSize() > 0
                ? Math.min(request.getSize(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        Pageable pageable = PageRequest.of(0, size + 1);

        List<Product> products;

        if (request.getCursor() == null || request.getCursor().isBlank()) {
            products = productRepository.searchProducts(
                    ProductStatus.ACTIVE,
                    request.getCategory(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    pageable
            );
        } else {
            Instant cursorCreatedAt = CursorUtils.decodeCreatedAt(request.getCursor());
            UUID cursorId = CursorUtils.decodeId(request.getCursor());
            products = productRepository.searchProductsWithCursor(
                    ProductStatus.ACTIVE,
                    request.getCategory(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    cursorCreatedAt,
                    cursorId,
                    pageable
            );
        }

        boolean hasMore = products.size() > size;
        List<Product> page = hasMore ? products.subList(0, size) : products;

        String nextCursor = hasMore
                ? CursorUtils.encode(
                page.get(page.size() - 1).getCreatedAt(),
                page.get(page.size() - 1).getId())
                : null;

        List<ProductResponse> responses = page.stream()
                .map(p -> {
                    Inventory inv = inventoryRepository.findByProductId(p.getId()).orElse(null);
                    return toResponse(p, inv);
                })
                .toList();

        return PagedResponse.<ProductResponse>builder()
                .items(responses)
                .pageSize(page.size())
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .build();
    }
}