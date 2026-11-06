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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceImplTest {

    @Mock
    private PurchaseEventPublisher eventPublisher;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ProductClient productClient;

    @InjectMocks
    private PurchaseServiceImpl service;

    // El servicio registra la publicación a Kafka "después del commit": simulamos una transacción activa.
    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private void completeCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private ProductResponse product(Long id, String name, double price) {
        return new ProductResponse(id, name, price, 10, "desc", null);
    }

    private Purchase purchase(Long id, OrderStatus status, List<Long> productIds) {
        return Purchase.builder()
                .id(id)
                .userId(7L)
                .productIds(productIds)
                .total(30.0)
                .purchaseDate(LocalDateTime.of(2026, 10, 1, 10, 0))
                .status(status)
                .build();
    }

    private PurchaseRequest request(Long userId, Long... productIds) {
        PurchaseRequest request = new PurchaseRequest();
        request.setUserId(userId);
        request.setProductIds(List.of(productIds));
        return request;
    }

    // ---------- createPurchase ----------

    @Test
    void createPurchase_reservesStockAndPublishesEventAfterCommit() {
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.findById(2L)).thenReturn(product(2L, "Paracetamol", 5.5));
        when(productClient.checkStock(1L, 1)).thenReturn(true);
        when(productClient.checkStock(2L, 1)).thenReturn(true);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        PurchaseResponse response = service.createPurchase(request(7L, 1L, 2L));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(response.getTotal()).isEqualTo(18.0);
        assertThat(response.getProducts()).extracting(PurchaseResponse.PurchaseItemResponse::getProductName)
                .containsExactly("Ibuprofeno", "Paracetamol");
        verify(productClient).reduceStock(1L, 1);
        verify(productClient).reduceStock(2L, 1);

        // Antes del commit todavía no se publica nada
        verify(eventPublisher, never()).publishPurchaseCreated(any());
        commit();

        ArgumentCaptor<PurchaseEvent> captor = ArgumentCaptor.forClass(PurchaseEvent.class);
        verify(eventPublisher).publishPurchaseCreated(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RESERVED");
        assertThat(captor.getValue().getPurchaseId()).isEqualTo(10L);
        assertThat(captor.getValue().getProductNames()).containsExactly("Ibuprofeno", "Paracetamol");
    }

    @Test
    void createPurchase_withoutStock_failsBeforeSavingOrReducingStock() {
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.checkStock(1L, 1)).thenReturn(false);

        assertThatThrownBy(() -> service.createPurchase(request(7L, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(purchaseRepository, never()).save(any());
        verify(productClient, never()).reduceStock(any(), any());
    }

    @Test
    void createPurchase_whenAStockReductionFails_restoresAlreadyReservedStock() {
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.findById(2L)).thenReturn(product(2L, "Paracetamol", 5.5));
        when(productClient.checkStock(1L, 1)).thenReturn(true);
        when(productClient.checkStock(2L, 1)).thenReturn(true);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            Long productId = invocation.getArgument(0);
            if (productId == 2L) {
                throw new RuntimeException("product-service caído");
            }
            return null;
        }).when(productClient).reduceStock(anyLong(), anyInt());

        assertThatThrownBy(() -> service.createPurchase(request(7L, 1L, 2L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("caído");

        // El primer producto ya se había descontado: se devuelve.
        verify(productClient).restoreStock(1L, 1);
        verify(productClient, never()).restoreStock(2L, 1);
        verify(eventPublisher, never()).publishPurchaseCreated(any());
    }

    @Test
    void createPurchase_whenTransactionRollsBack_restoresReservedStock() {
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.findById(2L)).thenReturn(product(2L, "Paracetamol", 5.5));
        when(productClient.checkStock(1L, 1)).thenReturn(true);
        when(productClient.checkStock(2L, 1)).thenReturn(true);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPurchase(request(7L, 1L, 2L));

        verify(productClient, never()).restoreStock(any(), any());
        rollback();

        verify(productClient).restoreStock(1L, 1);
        verify(productClient).restoreStock(2L, 1);
        verify(eventPublisher, never()).publishPurchaseCreated(any());
    }

    @Test
    void createPurchase_whenKafkaFails_doesNotBreakTheReservation() {
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.checkStock(1L, 1)).thenReturn(true);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("kafka caído")).when(eventPublisher).publishPurchaseCreated(any());

        PurchaseResponse response = service.createPurchase(request(7L, 1L));

        commit(); // no debe lanzar excepción
        assertThat(response.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    // ---------- consultas ----------

    @Test
    void findByUserId_returnsOrdersWithProductDetails() {
        when(purchaseRepository.findByUserIdOrderByPurchaseDateDesc(7L))
                .thenReturn(List.of(purchase(1L, OrderStatus.READY, List.of(1L))));
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));

        List<PurchaseResponse> result = service.findByUserId(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.READY);
        assertThat(result.get(0).getProducts().get(0).getProductName()).isEqualTo("Ibuprofeno");
    }

    @Test
    void findAll_withoutStatus_returnsEveryOrderNewestFirst() {
        when(purchaseRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(purchase(1L, OrderStatus.RESERVED, List.of(1L))));
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));

        List<PurchaseResponse> result = service.findAll(null);

        assertThat(result).hasSize(1);
        verify(purchaseRepository, never()).findByStatusOrderByPurchaseDateDesc(any());
    }

    @Test
    void findAll_withStatus_filtersByStatus() {
        when(purchaseRepository.findByStatusOrderByPurchaseDateDesc(OrderStatus.READY))
                .thenReturn(List.of(purchase(2L, OrderStatus.READY, List.of(1L))));
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));

        List<PurchaseResponse> result = service.findAll(OrderStatus.READY);

        assertThat(result).extracting(PurchaseResponse::getId).containsExactly(2L);
        verify(purchaseRepository, never()).findAll(any(Sort.class));
    }

    // ---------- updateStatus ----------

    @Test
    void updateStatus_reservedToReady_publishesEventWithNewStatus() {
        Purchase existing = purchase(1L, OrderStatus.RESERVED, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(purchaseRepository.save(existing)).thenReturn(existing);
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));

        PurchaseResponse response = service.updateStatus(1L, OrderStatus.READY);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY);
        verify(productClient, never()).restoreStock(any(), any());
        commit();

        ArgumentCaptor<PurchaseEvent> captor = ArgumentCaptor.forClass(PurchaseEvent.class);
        verify(eventPublisher).publishPurchaseCreated(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("READY");
    }

    @Test
    void updateStatus_cancelled_restoresStockOfEveryProduct() {
        Purchase existing = purchase(1L, OrderStatus.READY, List.of(1L, 2L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(purchaseRepository.save(existing)).thenReturn(existing);
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));
        when(productClient.findById(2L)).thenReturn(product(2L, "Paracetamol", 5.5));

        PurchaseResponse response = service.updateStatus(1L, OrderStatus.CANCELLED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productClient).restoreStock(1L, 1);
        verify(productClient).restoreStock(2L, 1);
    }

    @Test
    void updateStatus_cancelled_whenRestoreFails_undoesPartialRestore() {
        Purchase existing = purchase(1L, OrderStatus.READY, List.of(1L, 2L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            Long productId = invocation.getArgument(0);
            if (productId == 2L) {
                throw new RuntimeException("product-service caído");
            }
            return null;
        }).when(productClient).restoreStock(anyLong(), anyInt());

        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.CANCELLED))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("caído");

        verify(productClient).restoreStock(1L, 1);
        verify(productClient).reduceStock(1L, 1);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void updateStatus_cancelled_whenTransactionRollsBack_reducesStockAgain() {
        Purchase existing = purchase(1L, OrderStatus.READY, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(purchaseRepository.save(existing)).thenReturn(existing);
        when(productClient.findById(1L)).thenReturn(product(1L, "Ibuprofeno", 12.5));

        service.updateStatus(1L, OrderStatus.CANCELLED);

        verify(productClient).restoreStock(1L, 1);
        rollback();
        verify(productClient).reduceStock(1L, 1);
    }

    @Test
    void updateStatus_cancelled_whenSaveFails_reducesStockAgain() {
        Purchase existing = purchase(1L, OrderStatus.READY, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(purchaseRepository.save(existing)).thenThrow(new RuntimeException("db caída"));

        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.CANCELLED))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db caída");

        verify(productClient).restoreStock(1L, 1);
        rollback();
        verify(productClient).reduceStock(1L, 1);
    }

    @Test
    void updateStatus_withInvalidTransition_throwsAndDoesNotSave() {
        Purchase existing = purchase(1L, OrderStatus.PICKED_UP, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transición no permitida");

        verify(purchaseRepository, never()).save(any());
        verify(productClient, never()).restoreStock(any(), any());
    }

    @Test
    void updateStatus_whenMissing_throwsNotFound() {
        when(purchaseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, OrderStatus.READY))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    // ---------- deletePurchase ----------

    @Test
    void deletePurchase_cancelled_removesOrderWithoutTouchingStock() {
        Purchase existing = purchase(1L, OrderStatus.CANCELLED, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deletePurchase(1L);

        verify(purchaseRepository).delete(existing);
        verify(productClient, never()).restoreStock(any(), any());
        verify(productClient, never()).reduceStock(any(), any());
    }

    @Test
    void deletePurchase_reserved_restoresStockBeforeDeleting() {
        Purchase existing = purchase(1L, OrderStatus.RESERVED, List.of(1L, 2L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deletePurchase(1L);

        verify(productClient).restoreStock(1L, 1);
        verify(productClient).restoreStock(2L, 1);
        verify(purchaseRepository).delete(existing);
        completeCommit();
        verify(productClient, never()).reduceStock(any(), any());
    }

    @Test
    void deletePurchase_ready_restoresStockBeforeDeleting() {
        Purchase existing = purchase(1L, OrderStatus.READY, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deletePurchase(1L);

        verify(productClient).restoreStock(1L, 1);
        verify(purchaseRepository).delete(existing);
    }

    @Test
    void deletePurchase_whenDeleteRollsBack_reducesStockAgain() {
        Purchase existing = purchase(1L, OrderStatus.RESERVED, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deletePurchase(1L);
        verify(productClient).restoreStock(1L, 1);

        rollback();

        verify(productClient).reduceStock(1L, 1);
    }

    @Test
    void deletePurchase_pickedUp_removesOrderWithoutTouchingStock() {
        Purchase existing = purchase(1L, OrderStatus.PICKED_UP, List.of(1L));
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deletePurchase(1L);

        verify(purchaseRepository).delete(existing);
        verify(productClient, never()).restoreStock(any(), any());
        verify(productClient, never()).reduceStock(any(), any());
    }

    @Test
    void deletePurchase_whenMissing_throwsNotFound() {
        when(purchaseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePurchase(1L))
                .isInstanceOf(PurchaseNotFoundException.class);
        verify(purchaseRepository, never()).delete(any());
    }
}
