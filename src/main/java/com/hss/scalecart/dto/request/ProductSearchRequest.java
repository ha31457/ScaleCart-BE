package com.hss.scalecart.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String cursor;   // Base64 encoded "createdAt:id"
    private int size;
}