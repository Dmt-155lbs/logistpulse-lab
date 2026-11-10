package com.logistpulse.lab.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lote de eventos pendientes, en orden de inserción. FOR UPDATE SKIP LOCKED: si hay varias instancias de la API
     * ejecutando el publicador, cada una bloquea filas distintas y se salta las que otra ya está procesando
     * (sin esperas ni doble publicación en el caso normal). Funciona en PostgreSQL y en H2 2.x. Debe llamarse
     * dentro de una transacción: los bloqueos se sueltan al confirmarla.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE status = 'PENDING' ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);
}
