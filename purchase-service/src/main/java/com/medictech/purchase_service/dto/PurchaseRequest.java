package com.medictech.purchase_service.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequest {
    // El userId real se toma del JWT (header X-User-Id), no de este campo
    private Long userId;

    @NotEmpty(message = "Debes incluir al menos un producto")
    private List<Long> productIds;
}
