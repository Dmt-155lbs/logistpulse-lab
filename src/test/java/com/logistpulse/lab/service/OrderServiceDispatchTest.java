package com.logistpulse.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.domain.Warehouse;
import com.logistpulse.lab.integration.CarrierClient;
import com.logistpulse.lab.outbox.OutboxService;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import com.logistpulse.lab.repo.WarehouseRepository;
import com.logistpulse.lab.route.Point;
import com.logistpulse.lab.route.RouteDto;
import com.logistpulse.lab.route.RoutePlanner;
import com.logistpulse.lab.support.FakeTxManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Parte 6.3: dispatch ya no mantiene una transacción abierta durante la llamada HTTP, y re-valida al escribir. */
class OrderServiceDispatchTest {

    private OrderRepository orders;
    private RobotRepository robots;
    private RoutePlanner planner;
    private CarrierClient carrier;
    private OutboxService outbox;
    private FakeTxManager tm;
    private OrderService service;

    private Order order;
    private Robot robot;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        robots = mock(RobotRepository.class);
        planner = mock(RoutePlanner.class);
        carrier = mock(CarrierClient.class);
        outbox = mock(OutboxService.class);
        tm = new FakeTxManager();
        service = new OrderService(orders, robots, mock(WarehouseRepository.class), planner, carrier, outbox, tm,
                new SimpleMeterRegistry());

        Warehouse w = new Warehouse("CD", 0, 0);
        order = new Order("LP-T1", "cliente", 5, 3, w);
        ReflectionTestUtils.setField(order, "id", 7L);
        robot = new Robot("AMR-01", w, 100);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                .thenReturn(List.of(robot));
        when(planner.plan(any(Point.class), any(Point.class))).thenReturn(new RouteDto(List.of(), 9, 9));
    }

    @Test
    void laLlamadaAlTransportistaSeHaceFueraDeCualquierTransaccion() {
        boolean[] txDuranteLaLlamada = {true};
        when(carrier.etaMinutes(anyString())).thenAnswer(inv -> {
            txDuranteLaLlamada[0] = tm.inTransaction();
            return Optional.of(25);
        });

        var dto = service.dispatch(7L);

        assertThat(txDuranteLaLlamada[0]).as("había una transacción abierta durante el HTTP").isFalse();
        assertThat(tm.begun).isEqualTo(2);        // una de lectura antes y una de escritura después
        assertThat(tm.committed).isEqualTo(2);
        assertThat(dto.status()).isEqualTo(OrderStatus.DISPATCHED);
        assertThat(dto.etaMinutes()).isEqualTo(25);
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.BUSY);
        verify(outbox).record(OutboxService.ORDER_DISPATCHED, "7");
    }

    @Test
    void conElTransportistaCaidoUsaCostoPorDos() {
        when(carrier.etaMinutes(anyString())).thenReturn(Optional.empty());

        var dto = service.dispatch(7L);

        assertThat(dto.etaMinutes()).isEqualTo(18);   // 9 * 2
    }

    @Test
    void sinRobotsLibresDevuelve409YNoLlamaAlTransportista() {
        when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.dispatch(7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(carrier, never()).etaMinutes(anyString());
        verify(outbox, never()).record(anyString(), anyString());
    }

    @Test
    void pedidoYaDespachadoDevuelve409YNoLlamaAlTransportista() {
        order.setStatus(OrderStatus.DISPATCHED);

        assertThatThrownBy(() -> service.dispatch(7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(carrier, never()).etaMinutes(anyString());
    }

    @Test
    void pedidoInexistenteDevuelve404() {
        assertThatThrownBy(() -> service.dispatch(99L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void siOtroHiloDespachaDuranteLaLlamadaHttpLaRevalidacionDa409() {
        when(carrier.etaMinutes(anyString())).thenAnswer(inv -> {
            order.setStatus(OrderStatus.DISPATCHED);   // «otro hilo» despachó mientras esperábamos al transportista
            return Optional.of(25);
        });

        assertThatThrownBy(() -> service.dispatch(7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(outbox, never()).record(anyString(), anyString());
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);   // la transacción de escritura se revirtió
        assertThat(tm.rolledBack).isEqualTo(1);
    }

    @Test
    void siSeAcabanLosRobotsDuranteLaLlamadaHttpLaRevalidacionDa409() {
        when(carrier.etaMinutes(anyString())).thenAnswer(inv -> {
            when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                    .thenReturn(List.of());
            return Optional.of(25);
        });

        assertThatThrownBy(() -> service.dispatch(7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(outbox, never()).record(anyString(), anyString());
    }
}
