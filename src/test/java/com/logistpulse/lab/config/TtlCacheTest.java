package com.logistpulse.lab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TtlCacheTest {

    @Test
    void reutilizaLaLecturaDentroDelTtl() {
        AtomicInteger calls = new AtomicInteger();
        TtlCache<Integer> cache = new TtlCache<>(calls::incrementAndGet, Duration.ofSeconds(10));

        assertThat(cache.get()).isEqualTo(1);
        assertThat(cache.get()).isEqualTo(1);
        assertThat(calls).hasValue(1);
    }

    @Test
    void conTtlCeroVuelveAConsultar() {
        AtomicInteger calls = new AtomicInteger();
        TtlCache<Integer> cache = new TtlCache<>(calls::incrementAndGet, Duration.ZERO);

        cache.get();
        cache.get();
        assertThat(calls).hasValue(2);
    }

    @Test
    void memorizaTambienElFalloParaNoMartillarUnaBdCaida() {
        AtomicInteger calls = new AtomicInteger();
        TtlCache<Integer> cache = new TtlCache<>(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bd caida");
        }, Duration.ofSeconds(10));

        assertThatThrownBy(cache::get).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(cache::get).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }
}
