package com.logistpulse.lab.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.messaging.DispatchedEventHandler.Outcome;
import com.logistpulse.lab.service.OrderService;
import com.logistpulse.lab.support.FakeTxManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Parte 7.5: el consumidor tolera mensajes duplicados. */
class DispatchedEventHandlerTest {

    private OrderService orders;
    private ProcessedMessageRepository processed;
    private DispatchedEventHandler handler;
    private final Set<String> table = new HashSet<>();

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        processed = mock(ProcessedMessageRepository.class);
        // «tabla» en memoria
        when(processed.existsById(any())).thenAnswer(inv -> table.contains(inv.<String>getArgument(0)));
        when(processed.saveAndFlush(any())).thenAnswer(inv -> {
            ProcessedMessage m = inv.getArgument(0);
            table.add(m.getMessageId());
            return m;
        });
        handler = new DispatchedEventHandler(orders, processed, new TransactionTemplate(new FakeTxManager()));
    }

    @Test
    void elPrimerMensajeSeProcesaYQuedaRegistrado() {
        assertThat(handler.handle("10", 5L)).isEqualTo(Outcome.PROCESSED);

        verify(orders).transition(5L, OrderStatus.IN_TRANSIT);
        assertThat(table).containsExactly("10");
    }

    @Test
    void elMismoMensajeDosVecesSoloTransicionaUnaVez() {
        assertThat(handler.handle("10", 5L)).isEqualTo(Outcome.PROCESSED);
        assertThat(handler.handle("10", 5L)).isEqualTo(Outcome.DUPLICATE);

        verify(orders, times(1)).transition(5L, OrderStatus.IN_TRANSIT);
    }

    @Test
    void mensajesDistintosSeProcesanCadaUno() {
        handler.handle("10", 5L);
        handler.handle("11", 6L);

        verify(orders).transition(5L, OrderStatus.IN_TRANSIT);
        verify(orders).transition(6L, OrderStatus.IN_TRANSIT);
    }

    @Test
    void unaTransicionIlegalSeDescartaSinLanzarYSinRegistrar() {
        // p. ej. duplicado sin id, o con el registro ya purgado: el pedido ya está IN_TRANSIT
        doThrow(new ApiException(409, "Transición ilegal IN_TRANSIT → IN_TRANSIT"))
                .when(orders).transition(eq(5L), eq(OrderStatus.IN_TRANSIT));

        assertThat(handler.handle(null, 5L)).isEqualTo(Outcome.DISCARDED);   // no lanza: no habrá reentrega infinita
        assertThat(handler.handle("12", 5L)).isEqualTo(Outcome.DISCARDED);
        assertThat(table).as("un descarte no se marca como procesado").isEmpty();
    }

    @Test
    void sinMessageIdSoloDefiendeLaMaquinaDeEstados() {
        assertThat(handler.handle(null, 5L)).isEqualTo(Outcome.PROCESSED);

        verify(processed, never()).saveAndFlush(any());
    }

    @Test
    void unErrorDeInfraestructuraSiSePropagaParaQueRabbitReintente() {
        doThrow(new IllegalStateException("BD caída")).when(orders).transition(5L, OrderStatus.IN_TRANSIT);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle("10", 5L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(table).isEmpty();
    }
}
