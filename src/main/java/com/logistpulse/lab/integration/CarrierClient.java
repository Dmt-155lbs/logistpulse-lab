package com.logistpulse.lab.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del transportista externo (en el laboratorio lo simula WireMock).
 * Si el servicio falla o tarda más de 2 s, devuelve vacío y el llamador usa un ETA local (degradación elegante).
 */
@Component
public class CarrierClient {

    private static final Logger log = LoggerFactory.getLogger(CarrierClient.class);

    public record CarrierEta(String code, int etaMinutes) {
    }

    private final RestClient client;

    public CarrierClient(RestClient.Builder builder, @Value("${lab.carrier.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Optional<Integer> etaMinutes(String orderCode) {
        try {
            CarrierEta eta = client.get()
                    .uri("/carrier/eta?code={code}", orderCode)
                    .retrieve()
                    .body(CarrierEta.class);
            return Optional.ofNullable(eta).map(CarrierEta::etaMinutes);
        } catch (RestClientException e) {
            log.warn("Transportista no disponible para {}: {}", orderCode, e.getMessage());
            return Optional.empty();
        }
    }
}
