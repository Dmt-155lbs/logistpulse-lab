package com.logistpulse.lab.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderInput(
        @Pattern(regexp = "^LP-[A-Z0-9]{3,10}$", message = "código con formato LP-XXXX") String code,
        @NotBlank @Size(max = 80) String customer,
        @Min(0) int destX,
        @Min(0) int destY) {
}
