package com.medictech.purchase_service.entity;

/**
 * Ciclo de vida de un pedido con reserva y recojo en tienda:
 * RESERVED -> READY -> PICKED_UP, y se puede CANCELLED antes de ser recogido.
 */
public enum OrderStatus {
    RESERVED,
    READY,
    PICKED_UP,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case RESERVED -> next == READY || next == CANCELLED;
            case READY -> next == PICKED_UP || next == CANCELLED;
            case PICKED_UP, CANCELLED -> false;
        };
    }
}
