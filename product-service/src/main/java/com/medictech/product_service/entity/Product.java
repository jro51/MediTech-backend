package com.medictech.product_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer stock;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_src")
    private String imageSrc;

    // Fecha de vencimiento (opcional). Permite alertar productos por vencer.
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    // Cuando el stock llega a este valor (o menos) se genera una alerta de stock bajo.
    @Column(name = "min_stock", nullable = false)
    @Builder.Default
    private Integer minStock = 5;

    // Medicamentos que solo se entregan con receta médica.
    @Column(name = "requires_prescription", nullable = false)
    @Builder.Default
    private Boolean requiresPrescription = false;
}
