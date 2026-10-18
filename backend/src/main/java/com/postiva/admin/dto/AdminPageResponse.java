package com.postiva.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminPageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> AdminPageResponse<T> from(Page<T> source) {
        return new AdminPageResponse<>(source.getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
