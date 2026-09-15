package com.ovengers.slotkey.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ovengers.slotkey.global.error.ErrorCode;

/**
 * 공통 API 응답 래퍼.
 * 
 * <pre>
 * 성공: { "status": "SUCCESS", "code": "OK", "message": "...", "data": {...} }
 * 실패: { "status": "FAIL",    "code": "SPACE_NOT_FOUND", "message": "...", "data": null }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        ResponseStatus status,
        String code,
        String message,
        T data) {
    /** 성공 응답 (200 OK) */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseStatus.SUCCESS, "OK", "요청이 성공적으로 처리되었습니다.", data);
    }

    /** 성공 응답 (data 없음) */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ResponseStatus.SUCCESS, "OK", "요청이 성공적으로 처리되었습니다.", null);
    }

    /** 실패 응답 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(ResponseStatus.FAIL, errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 실패 응답 (커스텀 메시지) */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(ResponseStatus.FAIL, errorCode.getCode(), message, null);
    }
}
