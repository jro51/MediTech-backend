package com.medictech.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
    private String description;
    private String imageSrc;
    private LocalDate expirationDate;
    private Integer minStock;
    private Boolean requiresPrescription;
}
