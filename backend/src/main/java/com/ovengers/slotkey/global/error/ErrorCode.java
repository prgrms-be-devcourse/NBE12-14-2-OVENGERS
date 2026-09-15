package com.ovengers.slotkey.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 데이터가 올바르지 않습니다."),

    // ── Space ─────────────────────────────────────────
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "존재하지 않는 공간입니다."),
    SPACE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "SPACE_INACTIVE", "예약이 불가능한 공간입니다."),
    INVALID_PRICE_UNIT(HttpStatus.BAD_REQUEST, "INVALID_PRICE_UNIT", "요금은 100원 단위여야 합니다."),
    INVALID_OPERATING_HOURS(HttpStatus.BAD_REQUEST, "INVALID_OPERATING_HOURS", "운영 종료 시각은 시작 시각보다 늦어야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
