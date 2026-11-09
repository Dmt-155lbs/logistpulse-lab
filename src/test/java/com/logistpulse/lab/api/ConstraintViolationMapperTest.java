package com.logistpulse.lab.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.logistpulse.lab.api.dto.OrderInput;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConstraintViolationMapperTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @SuppressWarnings("unchecked")
    void devuelve400ConLosCamposInvalidos() {
        var violations = validator.validate(new OrderInput("mal", "", -1, 0));

        Response r = new ConstraintViolationMapper().toResponse(new ConstraintViolationException(Set.copyOf(violations)));

        assertThat(r.getStatus()).isEqualTo(400);
        Map<String, Object> body = (Map<String, Object>) r.getEntity();
        List<Map<String, String>> list = (List<Map<String, String>>) body.get("violations");
        assertThat(list.stream().map(m -> m.get("field")).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("code", "customer", "destX");
    }
}
