package com.logistpulse.lab.api.dto;

import java.util.List;
import java.util.Map;

public record PageDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, String> links) {

    public PageDto<T> withLinks(Map<String, String> newLinks) {
        return new PageDto<>(content, page, size, totalElements, totalPages, newLinks);
    }
}
