package com.logistpulse.lab.integration;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del transportista externo (en el laboratorio lo simula WireMock).
 * Si el servicio falla o tarda más de 2 s, devuelve vacío y el llamador usa un ETA local (degradación elegante).
 *
 * Política de reintentos (Parte 6.4): hasta {@code maxRetries} reintentos (2 por defecto) con backoff exponencial
 * (200 ms, 400 ms) SOLO ante respuestas 5xx. Nunca se reintenta:
 *  - un 4xx: la petición es la culpable, repetirla da lo mismo;
 *  - un error de E/S (timeout de lectura o de conexión, conexión rechazada): con un timeout de 2 s, dos reintentos
 *    subirían la espera del cliente de 2 s a ~6 s, y un transportista que ya no contesta a tiempo suele estar saturado
 *    (reintentar agrava el problema). Además la petición pudo llegar a ejecutarse en el servidor.
 * Cada degradación incrementa {@code logistpulse.carrier.fallback{reason=...}}.
 */
@Component
public class CarrierClient {

    private static final Logger log = LoggerFactory.getLogger(CarrierClient.class);

    public record CarrierEta(String code, int etaMinutes) {
    }

    /** Pausa entre reintentos; se puede sustituir en las pruebas para no dormir de verdad. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private final RestClient client;
    private final MeterRegistry registry;
    private final int maxRetries;
    private final Duration backoff;
    private final Sleeper sleeper;

    @Autowired
    public CarrierClient(RestClient.Builder builder, MeterRegistry registry,
                         @Value("${lab.carrier.base-url}") String baseUrl,
                         @Value("${lab.carrier.max-retries:2}") int maxRetries,
                         @Value("${lab.carrier.retry-backoff-ms:200}") long backoffMs) {
        this(buildClient(builder, baseUrl), registry, maxRetries, Duration.ofMillis(backoffMs),
                d -> Thread.sleep(d.toMillis()));
    }

    CarrierClient(RestClient client, MeterRegistry registry, int maxRetries, Duration backoff, Sleeper sleeper) {
        this.client = client;
        this.registry = registry;
        this.maxRetries = maxRetries;
        this.backoff = backoff;
        this.sleeper = sleeper;
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Optional<Integer> etaMinutes(String orderCode) {
        long start = System.nanoTime();
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                CarrierEta eta = client.get()
                        .uri("/carrier/eta?code={code}", orderCode)
                        .retrieve()
                        .body(CarrierEta.class);
                if (eta == null) {
                    return fallback(orderCode, "empty_body", attempts, start, "respuesta sin cuerpo");
                }
                if (attempts > 1) {
                    log.info("Transportista respondió para {} tras {} intentos", orderCode, attempts);
                }
                return Optional.of(eta.etaMinutes());
            } catch (HttpServerErrorException e) {
                if (attempts <= maxRetries) {
                    Duration wait = backoff.multipliedBy(1L << (attempts - 1));
                    log.warn("Transportista devolvió {} para {} (intento {}/{}); reintento en {} ms",
                            e.getStatusCode().value(), orderCode, attempts, maxRetries + 1, wait.toMillis());
                    if (!pause(wait)) {
                        return fallback(orderCode, "interrupted", attempts, start, "hilo interrumpido");
                    }
                    continue;
                }
                return fallback(orderCode, "server_error", attempts, start, e.getMessage());
            } catch (HttpClientErrorException e) {
                return fallback(orderCode, "client_error", attempts, start, e.getMessage());
            } catch (ResourceAccessException e) {
                return fallback(orderCode, "io_error", attempts, start, e.getMessage());
            } catch (RestClientException e) {
                return fallback(orderCode, "other", attempts, start, e.getMessage());
            }
        }
    }

    private boolean pause(Duration d) {
        try {
            sleeper.sleep(d);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Optional<Integer> fallback(String code, String reason, int attempts, long startNanos, String detail) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        registry.counter("logistpulse.carrier.fallback", "reason", reason).increment();
        log.warn("DEGRADACIÓN: sin ETA del transportista para {} (motivo={}, intentos={}, {} ms): {}. "
                + "El llamador usará el ETA local (costo de ruta x 2)", code, reason, attempts, ms, detail);
        return Optional.empty();
    }
}
