package com.medictech.purchase_service.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "RESERVED, READY",
            "RESERVED, CANCELLED",
            "READY, PICKED_UP",
            "READY, CANCELLED"
    })
    void allowsValidTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "RESERVED, PICKED_UP",
            "RESERVED, RESERVED",
            "READY, RESERVED",
            "PICKED_UP, READY",
            "PICKED_UP, CANCELLED",
            "CANCELLED, READY",
            "CANCELLED, RESERVED"
    })
    void rejectsInvalidTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void finalStatesAllowNoTransition() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(OrderStatus.PICKED_UP.canTransitionTo(next)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(next)).isFalse();
        }
    }
}
