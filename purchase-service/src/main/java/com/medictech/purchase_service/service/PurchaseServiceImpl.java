package com.medictech.purchase_service.service;

import com.medictech.purchase_service.client.ProductClient;
import com.medictech.purchase_service.dto.ProductResponse;
import com.medictech.purchase_service.dto.PurchaseEvent;
import com.medictech.purchase_service.dto.PurchaseRequest;
import com.medictech.purchase_service.dto.PurchaseResponse;
import com.medictech.purchase_service.entity.OrderStatus;
import com.medictech.purchase_service.entity.Purchase;
import com.medictech.purchase_service.exception.PurchaseNotFoundException;
import com.medictech.purchase_service.messaging.PurchaseEventPublisher;
import com.medictech.purchase_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseServiceImpl implements PurchaseService {
    private final PurchaseEventPublisher eventPublisher;
    private final PurchaseRepository purchaseRepository;
    private final ProductClient productClient;

    @Override
    @Transactional
    public PurchaseResponse createPurchase(PurchaseRequest request) {
        log.info("Procesando reserva para usuario: {}", request.getUserId());

        // 1. Obtenemos los productos
        List<ProductResponse> products = fetchProducts(request.getProductIds());

        // 2. Verificamos stock
        for (ProductResponse product : products) {
            boolean hasStock = productClient.checkStock(product.getId(), 1);
            if (!hasStock) {
                throw new IllegalArgumentException(
                        "Stock insuficiente para: " + product.getName()
                );
            }
        }

        // 3. Calculamos el total
        double total = products.stream()
                .mapToDouble(ProductResponse::getPrice)
                .sum();

        // 4. Guardamos el pedido en DB (nace como RESERVED)
        Purchase purchase = Purchase.builder()
                .userId(request.getUserId())
                .productIds(request.getProductIds())
                .total(total)
                .purchaseDate(LocalDateTime.now())
                .status(OrderStatus.RESERVED)
                .build();

        Purchase saved = purchaseRepository.save(purchase);

        // 5. Reservamos el stock
        request.getProductIds().forEach(productId -> {
            log.info("Reduciendo stock del producto id: {}", productId);
            productClient.reduceStock(productId, 1);
        });

        log.info("Pedido creado en DB con id: {}, total: {}", saved.getId(), total);

        // 6. Publicamos el evento solo después de que la DB confirme el commit
        publishAfterCommit(buildEvent(saved, products));

        return toResponse(saved, products);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseResponse> findByUserId(Long userId) {
        log.info("Obteniendo compras del usuario: {}", userId);

        return purchaseRepository.findByUserIdOrderByPurchaseDateDesc(userId)
                .stream()
                .map(purchase -> toResponse(purchase, fetchProducts(purchase.getProductIds())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseResponse> findAll(OrderStatus status) {
        List<Purchase> purchases = status == null
                ? purchaseRepository.findAll(Sort.by(Sort.Direction.DESC, "purchaseDate"))
                : purchaseRepository.findByStatusOrderByPurchaseDateDesc(status);

        return purchases.stream()
                .map(purchase -> toResponse(purchase, fetchProducts(purchase.getProductIds())))
                .toList();
    }

    @Override
    @Transactional
    public PurchaseResponse updateStatus(Long id, OrderStatus newStatus) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));

        OrderStatus current = purchase.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Transición no permitida: " + current + " -> " + newStatus
            );
        }

        // Si se cancela la reserva, el stock vuelve al inventario
        if (newStatus == OrderStatus.CANCELLED) {
            purchase.getProductIds().forEach(productId -> {
                log.info("Restaurando stock del producto id: {}", productId);
                productClient.restoreStock(productId, 1);
            });
        }

        purchase.setStatus(newStatus);
        Purchase saved = purchaseRepository.save(purchase);
        log.info("Pedido {} pasó de {} a {}", id, current, newStatus);

        List<ProductResponse> products = fetchProducts(saved.getProductIds());
        publishAfterCommit(buildEvent(saved, products));

        return toResponse(saved, products);
    }

    @Override
    @Transactional
    public void deletePurchase(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
        purchaseRepository.delete(purchase);
        log.info("Compra eliminada con id: {}", id);
    }

    private List<ProductResponse> fetchProducts(List<Long> productIds) {
        return productIds.stream()
                .map(id -> {
                    log.info("Consultando producto id: {} en product-service", id);
                    return productClient.findById(id);
                })
                .toList();
    }

    private PurchaseEvent buildEvent(Purchase purchase, List<ProductResponse> products) {
        return PurchaseEvent.builder()
                .purchaseId(purchase.getId())
                .userId(purchase.getUserId())
                .productNames(products.stream().map(ProductResponse::getName).toList())
                .productPrices(products.stream().map(ProductResponse::getPrice).toList())
                .total(purchase.getTotal())
                .purchaseDate(purchase.getPurchaseDate())
                .status(purchase.getStatus().name())
                .build();
    }

    // PUBLICACIÓN SEGURA: se ejecuta solo después de que la DB confirma el commit
    private void publishAfterCommit(PurchaseEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    log.info("Commit en BD exitoso. Enviando evento a Kafka...");
                    eventPublisher.publishPurchaseCreated(event);
                } catch (Exception e) {
                    log.error("Fallo al enviar a Kafka post-commit: ", e);
                }
            }
        });
    }

    private PurchaseResponse toResponse(Purchase purchase, List<ProductResponse> products) {
        List<PurchaseResponse.PurchaseItemResponse> items = products.stream()
                .map(p -> PurchaseResponse.PurchaseItemResponse.builder()
                        .productId(p.getId())
                        .productName(p.getName())
                        .price(p.getPrice())
                        .build())
                .toList();

        return PurchaseResponse.builder()
                .id(purchase.getId())
                .userId(purchase.getUserId())
                .products(items)
                .total(purchase.getTotal())
                .purchaseDate(purchase.getPurchaseDate())
                .status(purchase.getStatus())
                .build();
    }
}
