package com.logistpulse.lab.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Política de reintentos del CarrierClient (Parte 6.4), con un servidor simulado y sin dormir de verdad. */
class CarrierClientTest {

    private static final String URL = "http://carrier/carrier/eta?code=LP-1";
    private static final String OK = "{\"code\":\"LP-1\",\"etaMinutes\":25}";

    private MockRestServiceServer server;
    private CarrierClient client;
    private SimpleMeterRegistry registry;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://carrier");
        server = MockRestServiceServer.bindTo(builder).build();
        registry = new SimpleMeterRegistry();
        client = new CarrierClient(builder.build(), registry, 2, Duration.ofMillis(200), d -> sleeps.add(d.toMillis()));
    }

    private double fallbacks(String reason) {
        var c = registry.find("logistpulse.carrier.fallback").tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void respuestaCorrectaNoReintenta() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.etaMinutes("LP-1")).contains(25);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void reintentaLos5xxConBackoffExponencialYTerminaBien() {
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.etaMinutes("LP-1")).contains(25);
        assertThat(sleeps).containsExactly(200L, 400L);
        assertThat(fallbacks("server_error")).isZero();
        server.verify();
    }

    @Test
    void conTresFallos5xxSeRindeTrasDosReintentos() {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.etaMinutes("LP-1")).isEmpty();
        assertThat(sleeps).containsExactly(200L, 400L);   // 3 intentos = 1 + 2 reintentos; no hay un cuarto
        assertThat(fallbacks("server_error")).isEqualTo(1);
        server.verify();
    }

    @Test
    void nuncaReintentaUn4xx() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.etaMinutes("LP-1")).isEmpty();
        assertThat(sleeps).isEmpty();
        assertThat(fallbacks("client_error")).isEqualTo(1);
        server.verify();   // exactamente 1 petición
    }

    @Test
    void nuncaReintentaUnErrorDeEntradaSalida() {
        // un timeout o una conexión rechazada llegan como ResourceAccessException
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(request -> {
            throw new IOException("Read timed out");
        });

        assertThat(client.etaMinutes("LP-1")).isEmpty();
        assertThat(sleeps).isEmpty();
        assertThat(fallbacks("io_error")).isEqualTo(1);
        server.verify();
    }
}
