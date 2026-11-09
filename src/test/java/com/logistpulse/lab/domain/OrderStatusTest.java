package com.logistpulse.lab.domain;

import static com.logistpulse.lab.domain.OrderStatus.CANCELLED;
import static com.logistpulse.lab.domain.OrderStatus.CREATED;
import static com.logistpulse.lab.domain.OrderStatus.DELIVERED;
import static com.logistpulse.lab.domain.OrderStatus.DISPATCHED;
import static com.logistpulse.lab.domain.OrderStatus.IN_TRANSIT;
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
        assertThat(DELIVERED.next()).isEmpty();
        assertThat(CANCELLED.next()).isEmpty();
    }
}
