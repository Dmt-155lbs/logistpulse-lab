package com.logistpulse.lab.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.logistpulse.lab.support.FakeTxManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxPublisherTest {

    private OutboxRepository repo;
    private final List<String> sent = new ArrayList<>();
    private boolean brokerDown;
    private OutboxPublisher publisher;
    private OutboxEvent e1;
    private OutboxEvent e2;

    @BeforeEach
    void setUp() {
        repo = mock(OutboxRepository.class);
        e1 = new OutboxEvent(OutboxService.ORDER_DISPATCHED, "1");
        e2 = new OutboxEvent(OutboxService.ORDER_DISPATCHED, "2");
        when(repo.lockPending(50)).thenAnswer(inv -> {
            List<OutboxEvent> pending = new ArrayList<>();
            for (OutboxEvent e : List.of(e1, e2)) {
                if (e.getStatus() == OutboxStatus.PENDING) {
                    pending.add(e);
                }
            }
            return pending;
        });
        EventSender sender = ev -> {
            if (brokerDown) {
                throw new IllegalStateException("broker caído");
            }
            sent.add(ev.getPayload());
        };
        publisher = new OutboxPublisher(repo, sender, new TransactionTemplate(new FakeTxManager()),
                new SimpleMeterRegistry(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), 50);
    }

    @Test
    void publicaEnOrdenYMarcaComoEnviados() {
        assertThat(publisher.publishPending()).isEqualTo(2);

        assertThat(sent).containsExactly("1", "2");
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e1.getPublishedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(publisher.publishPending()).isZero();   // ya no quedan pendientes: no se publica dos veces
    }

    @Test
    void siElBrokerEstaCaidoNoMarcaEnviadoCuentaIntentosYCortaElLote() {
        brokerDown = true;

        assertThat(publisher.publishPending()).isZero();

        assertThat(sent).isEmpty();
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(e1.getAttempts()).isEqualTo(1);
        assertThat(e1.getLastError()).contains("broker caído");
        assertThat(e2.getAttempts()).as("no se intenta el 2.º antes que el 1.º").isZero();
    }

    @Test
    void cuandoElBrokerVuelveSePublicaLoPendienteSinPerderElOrden() {
        brokerDown = true;
        publisher.publishPending();
        publisher.publishPending();
        assertThat(e1.getAttempts()).isEqualTo(2);

        brokerDown = false;
        assertThat(publisher.publishPending()).isEqualTo(2);

        assertThat(sent).containsExactly("1", "2");
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e1.getAttempts()).isEqualTo(3);
    }

    @Test
    void siFallaElSegundoElPrimeroSiQuedaEnviado() {
        EventSender flaky = ev -> {
            if ("2".equals(ev.getPayload())) {
                throw new IllegalStateException("fallo");
            }
        };
        OutboxPublisher p = new OutboxPublisher(repo, flaky, new TransactionTemplate(new FakeTxManager()),
                new SimpleMeterRegistry(), Clock.systemUTC(), 50);

        assertThat(p.publishPending()).isEqualTo(1);
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
