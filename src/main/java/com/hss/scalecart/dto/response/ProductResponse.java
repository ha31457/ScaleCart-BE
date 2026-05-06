package com.hss.scalecart.dto.response;

import com.hss.scalecart.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor      // ← add this — Jackson needs it to deserialize
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private ProductStatus status;
    private int availableStock;   // quantity - reserved, never raw quantity
    private UUID sellerId;
    private Instant createdAt;
}