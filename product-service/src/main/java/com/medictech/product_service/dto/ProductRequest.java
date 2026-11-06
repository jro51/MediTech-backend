package com.medictech.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a 0")
    private Double price;

    @NotNull(message = "El stock es obligatorio")
    @PositiveOrZero(message = "El stock no puede ser negativo")
    private Integer stock;

    private String description;

    private String imageSrc;

    // Opcional: si no se envía, el producto no tiene alerta de vencimiento.
    private LocalDate expirationDate;

    // Opcional: por defecto 5 unidades.
    @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
    private Integer minStock;

    // Opcional: por defecto false.
    private Boolean requiresPrescription;
}
