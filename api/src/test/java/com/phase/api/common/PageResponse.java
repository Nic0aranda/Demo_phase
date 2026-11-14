package com.phase.api.common;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int total = all.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<T> content = all.subList(from, to);
        return new PageResponse<>(
                List.copyOf(content),
                safePage,
                safeSize,
                total,
                totalPages,
                safePage == 0,
                totalPages == 0 || safePage >= totalPages - 1
        );
    }
}
