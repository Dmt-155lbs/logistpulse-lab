package com.logistpulse.lab.config;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Memoriza durante un tiempo corto el resultado (o el fallo) de una lectura costosa. Se usa en los gauges que consultan
 * la BD: Prometheus lee varios gauges cada 5 s y, si la BD está caída, cada uno esperaría el timeout de la pool; al
 * memorizar también el fallo, el scrape paga esa espera una sola vez por ventana y no se cuelga.
 */
final class TtlCache<T> {

    private record Entry<T>(T value, RuntimeException error, long takenAtNanos) {
    }

    private final Supplier<T> loader;
    private final long ttlNanos;
    private volatile Entry<T> entry;

    TtlCache(Supplier<T> loader, Duration ttl) {
        this.loader = loader;
        this.ttlNanos = ttl.toNanos();
    }

    T get() {
        Entry<T> e = entry;
        if (e == null || System.nanoTime() - e.takenAtNanos() >= ttlNanos) {
            synchronized (this) {
                e = entry;
                if (e == null || System.nanoTime() - e.takenAtNanos() >= ttlNanos) {
                    try {
                        e = new Entry<>(loader.get(), null, System.nanoTime());
                    } catch (RuntimeException ex) {
                        e = new Entry<>(null, ex, System.nanoTime());
                    }
                    entry = e;
                }
            }
        }
        if (e.error() != null) {
            throw e.error();
        }
        return e.value();
    }
}
