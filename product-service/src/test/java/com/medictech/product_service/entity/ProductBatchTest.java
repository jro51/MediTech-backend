package com.medictech.product_service.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBatchTest {

    private ProductBatch batch(Long id, LocalDate expirationDate, LocalDate receivedAt) {
        return ProductBatch.builder()
                .id(id)
                .product(Product.builder()
                        .id(1L)
                        .name("Ibuprofeno 400mg")
                        .price(10.0)
                        .stock(10)
                        .build())
                .batchNumber("LOTE-" + id)
                .expirationDate(expirationDate)
                .receivedAt(receivedAt)
                .quantity(5)
                .build();
    }

    @Test
    void fefoOrder_sortsByExpirationThenReceptionThenId() {
        ProductBatch sameExpirationFirstReceived = batch(1L, LocalDate.of(2027, 5, 1), LocalDate.of(2026, 1, 1));
        ProductBatch sameExpirationSecondReceived = batch(3L, LocalDate.of(2027, 5, 1), LocalDate.of(2026, 1, 2));
        ProductBatch sameExpirationSameReceivedLowerId = batch(0L, LocalDate.of(2027, 5, 1), LocalDate.of(2026, 1, 1));
        ProductBatch earlierExpiration = batch(2L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 2, 1));
        ProductBatch withoutExpiration = batch(4L, null, LocalDate.of(2025, 1, 1));

        List<ProductBatch> ordered = new ArrayList<>(List.of(
                sameExpirationFirstReceived,
                sameExpirationSecondReceived,
                sameExpirationSameReceivedLowerId,
                earlierExpiration,
                withoutExpiration
        ));
        ordered.sort(ProductBatch.FEFO_ORDER);

        assertThat(ordered).containsExactly(
                earlierExpiration,
                sameExpirationSameReceivedLowerId,
                sameExpirationFirstReceived,
                sameExpirationSecondReceived,
                withoutExpiration
        );
    }

    @Test
    void hasAvailableStock_reflectsQuantity() {
        ProductBatch withStock = batch(1L, LocalDate.of(2027, 5, 1), LocalDate.now());
        ProductBatch withoutStock = batch(2L, LocalDate.of(2027, 5, 1), LocalDate.now());
        ProductBatch withNullQuantity = batch(3L, LocalDate.of(2027, 5, 1), LocalDate.now());

        withoutStock.setQuantity(0);
        withNullQuantity.setQuantity(null);

        assertThat(withStock.hasAvailableStock()).isTrue();
        assertThat(withoutStock.hasAvailableStock()).isFalse();
        assertThat(withNullQuantity.hasAvailableStock()).isFalse();
    }

    @Test
    void builder_setsReceivedAtByDefault() {
        ProductBatch batch = ProductBatch.builder()
                .product(Product.builder().id(1L).build())
                .expirationDate(LocalDate.now().plusMonths(1))
                .quantity(10)
                .build();

        assertThat(batch.getReceivedAt()).isNotNull();
        assertThat(batch.getReceivedAt()).isBeforeOrEqualTo(LocalDate.now());
    }
}
