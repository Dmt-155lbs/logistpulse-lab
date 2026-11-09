package com.logistpulse.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import com.logistpulse.lab.repo.WarehouseRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Prueba de la lógica de listado del servicio con repositorios simulados (sin contexto Spring). */
class OrderServiceListTest {

    private OrderRepository orders;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        service = new OrderService(orders, mock(RobotRepository.class), mock(WarehouseRepository.class),
                null, null, null, new SimpleMeterRegistry());
        Page<Order> empty = new PageImpl<>(List.of());
        when(orders.findAll(any(Pageable.class))).thenReturn(empty);
        when(orders.findByStatus(any(), any(Pageable.class))).thenReturn(empty);
        when(orders.findByCustomerNameContainingIgnoreCase(any(), any(Pageable.class))).thenReturn(empty);
        when(orders.findByStatusAndCustomerNameContainingIgnoreCase(any(), any(), any(Pageable.class))).thenReturn(empty);
    }

    @Test
    void limitaElTamanoDePaginaA100() {
        var page = service.list(null, null, null, 0, 100000);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    void tamanoNegativoOCeroSeSubeA1YPaginaNegativaA0() {
        var page = service.list(null, null, null, -5, 0);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(0);
    }

    @Test
    void clienteEnBlancoNoFiltra() {
        service.list(null, "   ", null, 0, 10);
        verify(orders).findAll(any(Pageable.class));
    }

    @Test
    void clienteSeCombinaConEstado() {
        service.list(OrderStatus.CREATED, " cruz ", null, 0, 10);
        verify(orders).findByStatusAndCustomerNameContainingIgnoreCase(eq(OrderStatus.CREATED), eq("cruz"), any(Pageable.class));
    }
}
