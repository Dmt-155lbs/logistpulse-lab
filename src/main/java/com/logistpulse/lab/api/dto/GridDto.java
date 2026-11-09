package com.logistpulse.lab.api.dto;

import java.util.List;

public record GridDto(int width, int height, List<String> rows, String legend) {
}
