package com.logistpulse.lab.repo;

import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // El EntityGraph evita el problema N+1 al mapear warehouse y robot en el DTO.
    // Ejercicio: quítalo, activa spring.jpa.show-sql=true y cuenta las consultas.
    @Override
    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByCustomerNameContainingIgnoreCase(String customer, Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByStatusAndCustomerNameContainingIgnoreCase(OrderStatus status, String customer, Pageable pageable);

    boolean existsByCode(String code);

    /** Una sola consulta agregada (usa idx_orders_status): filas {estado, cantidad}. Alimenta logistpulse.orders.by.status. */
    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupedByStatus();

    /** Fecha de despacho del pedido DISPATCHED más antiguo (null si no hay ninguno). */
    @Query("select min(o.dispatchedAt) from Order o where o.status = com.logistpulse.lab.domain.OrderStatus.DISPATCHED")
    Instant oldestDispatchedAt();
}
