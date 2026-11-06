package com.medictech.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAlertsResponse {
    // Ventana usada para considerar un producto "por vencer"
    private int days;
    // Productos con stock <= stock mínimo
    private List<ProductResponse> lowStock;
    // Productos ya vencidos o que vencen dentro de la ventana
    private List<ProductResponse> expiringSoon;
}
