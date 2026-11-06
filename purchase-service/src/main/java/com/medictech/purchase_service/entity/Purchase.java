package com.medictech.purchase_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo guardamos el ID del usuario — no la entidad completa
    // Cada servicio tiene su propia BD (Database per Service pattern)
    @Column(nullable = false)
    private Long userId;

    @ElementCollection
    @CollectionTable(
            name = "purchase_items",
            joinColumns = @JoinColumn(name = "purchase_id")
    )
    @Column(name = "product_id")
    private List<Long> productIds;

    @Column(nullable = false)
    private Double total;

    @Column(nullable = false)
    private LocalDateTime purchaseDate;

    // Estado del pedido (reserva y recojo en tienda)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.RESERVED;
}
