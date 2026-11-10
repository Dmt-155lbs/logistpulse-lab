package com.logistpulse.lab.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

class ServiceUnavailableMapperTest {

    private final ServiceUnavailableMapper mapper = new ServiceUnavailableMapper();

    @Test
    void bdOCacheCaidaDa503ConRetryAfter() {
        var r = mapper.toResponse(new CannotCreateTransactionException("no hay conexion"));
        assertThat(r.getStatus()).isEqualTo(503);
        assertThat(r.getHeaderString("Retry-After")).isEqualTo("2");
        assertThat(mapper.toResponse(new QueryTimeoutException("Redis command timed out")).getStatus()).isEqualTo(503);
    }

    @Test
    void detectaLaCausaAnidada() {
        var e = new DataIntegrityViolationException("envoltura", new DataAccessResourceFailureException("conexion perdida"));
        assertThat(mapper.toResponse(e).getStatus()).isEqualTo(503);
    }

    @Test
    void otrosErroresSiguenSiendo500() {
        assertThat(mapper.toResponse(new DataIntegrityViolationException("duplicado")).getStatus()).isEqualTo(500);
    }
}
