package com.logistpulse.lab.domain;

import static com.logistpulse.lab.domain.OrderStatus.CANCELLED;
import static com.logistpulse.lab.domain.OrderStatus.CREATED;
import static com.logistpulse.lab.domain.OrderStatus.DELIVERED;
import static com.logistpulse.lab.domain.OrderStatus.DISPATCHED;
import static com.logistpulse.lab.domain.OrderStatus.IN_TRANSIT;
import static com.logistpulse.lab.domain.OrderStatus.RETURNED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPath() {
        assertThat(CREATED.canGoTo(DISPATCHED)).isTrue();
        assertThat(DISPATCHED.canGoTo(IN_TRANSIT)).isTrue();
        assertThat(IN_TRANSIT.canGoTo(DELIVERED)).isTrue();
    }

    @Test
    void cannotSkipStates() {
        assertThat(CREATED.canGoTo(IN_TRANSIT)).isFalse();
        assertThat(CREATED.canGoTo(DELIVERED)).isFalse();
        assertThat(DISPATCHED.canGoTo(DELIVERED)).isFalse();
    }

    @Test
    void cannotCancelOnceInTransit() {
        assertThat(IN_TRANSIT.canGoTo(CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoExit() {
        assertThat(CANCELLED.next()).isEmpty();
        assertThat(RETURNED.next()).isEmpty();
    }

    @Test
    void deliveredCanBeReturned() {
        assertThat(DELIVERED.canGoTo(RETURNED)).isTrue();
        assertThat(DELIVERED.next()).containsExactly(RETURNED);
    }

    @Test
    void onlyDeliveredCanBeReturned() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.canGoTo(RETURNED)).as("%s -> RETURNED", s).isEqualTo(s == DELIVERED);
        }
    }

    @Test
    void returnedIsTerminal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(RETURNED.canGoTo(target)).as("RETURNED -> %s", target).isFalse();
        }
    }

    @Test
    void deliveredCannotGoBackOrBeCancelled() {
        assertThat(DELIVERED.canGoTo(CANCELLED)).isFalse();
        assertThat(DELIVERED.canGoTo(IN_TRANSIT)).isFalse();
        assertThat(DELIVERED.canGoTo(CREATED)).isFalse();
    }

    @Test
    void returnNotReachableBeforeDelivery() {
        assertThat(CREATED.canGoTo(RETURNED)).isFalse();
        assertThat(DISPATCHED.canGoTo(RETURNED)).isFalse();
        assertThat(IN_TRANSIT.canGoTo(RETURNED)).isFalse();
        assertThat(CANCELLED.canGoTo(RETURNED)).isFalse();
    }
}
