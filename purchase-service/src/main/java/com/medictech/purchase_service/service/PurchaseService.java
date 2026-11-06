package com.medictech.purchase_service.service;

import com.medictech.purchase_service.dto.PurchaseRequest;
import com.medictech.purchase_service.dto.PurchaseResponse;
import com.medictech.purchase_service.entity.OrderStatus;

import java.util.List;

public interface PurchaseService {
    PurchaseResponse createPurchase(PurchaseRequest request);

    List<PurchaseResponse> findByUserId(Long userId);

    // Para el panel de la botica: todos los pedidos o filtrados por estado
    List<PurchaseResponse> findAll(OrderStatus status);

    // Avanza (o cancela) un pedido respetando las transiciones válidas
    PurchaseResponse updateStatus(Long id, OrderStatus newStatus);

    void deletePurchase(Long id);
}
