package com.ovengers.slotkey.global.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring Data Page 를 클라이언트 친화적인 형태로 변환하는 페이지네이션 응답 래퍼.
 * 
 * <pre>
 * { "content": [...], "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
