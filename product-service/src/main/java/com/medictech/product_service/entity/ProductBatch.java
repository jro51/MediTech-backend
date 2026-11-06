package com.medictech.product_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Lote de un producto: unidades con la misma fecha de vencimiento.
 *
 * <p>Un producto puede tener varios lotes. La salida por FEFO (First Expired,
 * First Out) se resuelve ordenando por vencimiento, luego por fecha de recepción
 * y finalmente por id para tener un orden determinista.</p>
 */
@Entity
@Table(
        name = "product_batches",
        indexes = @Index(
                name = "idx_product_batches_product_expiration",
                columnList = "product_id, expiration_date"
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatch {

    /**
     * Orden FEFO: primero vence, primero sale. Los lotes sin vencimiento quedan
     * al final y, en caso de empate, se prioriza el que llegó antes.
     */
    public static final Comparator<ProductBatch> FEFO_ORDER =
            Comparator.comparing(
                            ProductBatch::getExpirationDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            ProductBatch::getReceivedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            ProductBatch::getId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Código o número de lote del proveedor. Es opcional.
    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    // Fecha de vencimiento del lote. Puede ser nula para productos que no vencen.
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    // Unidades disponibles en este lote.
    @Column(nullable = false)
    private Integer quantity;

    // Fecha en la que ingresó el lote; sirve para desempatar vencimientos iguales.
    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDate receivedAt = LocalDate.now();

    public boolean hasAvailableStock() {
        return quantity != null && quantity > 0;
    }

    @PrePersist
    void ensureReceivedAt() {
        if (receivedAt == null) {
            receivedAt = LocalDate.now();
        }
    }
}
