package com.logistpulse.lab.api.dto;

import com.logistpulse.lab.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionInput(@NotNull OrderStatus to) {
}
