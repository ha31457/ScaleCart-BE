package com.hss.scalecart.controller;

import com.hss.scalecart.dto.request.CreateProductRequest;
import com.hss.scalecart.dto.response.ApiResponse;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.dto.response.ProductResponse;
import com.hss.scalecart.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @RequestAttribute("userId") String userId) {

        ProductResponse response = productService.createProduct(
                request, UUID.fromString(userId));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Product created"));
    }

    // Public endpoint — any user (even unauthenticated) can browse products
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getProduct(id), "Product found"));
    }

    // Public listing with keyset pagination
    // Usage: GET /api/v1/products?category=electronics&pageSize=20
    // Next page: GET /api/v1/products?category=electronics&lastSeenId=<nextCursor>&pageSize=20
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID lastSeenId,
            @RequestParam(defaultValue = "20") int pageSize) {

        if (pageSize > 100) pageSize = 100;  // hard cap — prevent abuse

        return ResponseEntity.ok(ApiResponse.success(
                productService.listProducts(category, lastSeenId, pageSize),
                "Products fetched"));
    }
}