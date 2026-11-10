package com.logistpulse.lab.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Relé del patrón Outbox: cada {@code lab.outbox.poll-ms} toma un lote de eventos PENDING (en orden, con
 * FOR UPDATE SKIP LOCKED), los entrega al {@link EventSender} y marca como SENT los que salieron.
 *
 * Garantía: al-menos-una-vez. Si el broker está caído el evento NO se marca enviado, se cuenta el intento y se
 * corta el lote (así se conserva el orden); se reintenta en el siguiente ciclo. Si el proceso cae después de enviar y
 * antes de confirmar la transacción, el evento se enviará otra vez: por eso el consumidor debe ser idempotente.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repo;
    private final EventSender sender;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Counter published;
    private final Counter failures;

    @Autowired
    public OutboxPublisher(OutboxRepository repo, EventSender sender, PlatformTransactionManager tm,
                           MeterRegistry registry, @Value("${lab.outbox.batch-size:50}") int batchSize) {
        this(repo, sender, new TransactionTemplate(tm), registry, Clock.systemUTC(), batchSize);
    }

    OutboxPublisher(OutboxRepository repo, EventSender sender, TransactionTemplate tx, MeterRegistry registry,
                    Clock clock, int batchSize) {
        this.repo = repo;
        this.sender = sender;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.published = Counter.builder("logistpulse.outbox.published").description("Eventos outbox publicados").register(registry);
        this.failures = Counter.builder("logistpulse.outbox.failures").description("Fallos al publicar eventos outbox").register(registry);
    }

    @Scheduled(fixedDelayString = "${lab.outbox.poll-ms:1000}")
    public void poll() {
        try {
            publishPending();
        } catch (RuntimeException e) {
            // p. ej. base de datos caída: se reintenta en el siguiente ciclo
            log.warn("Outbox: no se pudo procesar el lote: {}", e.toString());
        }
    }

    /** @return cuántos eventos se publicaron en este ciclo */
    public int publishPending() {
        Integer sent = tx.execute(status -> {
            List<OutboxEvent> batch = repo.lockPending(batchSize);
            int ok = 0;
            for (OutboxEvent e : batch) {
                try {
                    sender.send(e);
                } catch (RuntimeException ex) {
                    e.markFailed(ex.toString());
                    failures.increment();
                    log.warn("Outbox: no se pudo publicar outboxId={} (intento {}): {}. Queda PENDING y se reintenta",
                            e.getId(), e.getAttempts(), ex.toString());
                    break;   // conserva el orden: no se publican los siguientes antes que este
                }
                e.markSent(clock.instant());
                published.increment();
                ok++;
            }
            return ok;
        });
        return sent == null ? 0 : sent;
    }
}
