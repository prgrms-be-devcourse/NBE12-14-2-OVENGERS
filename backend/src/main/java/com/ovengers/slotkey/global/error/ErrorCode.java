package com.ovengers.slotkey.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 데이터가 올바르지 않습니다."),
    // ── Member ─────────────────────────────────────────
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "존재하지 않는 회원입니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRM_MISMATCH", "비밀번호가 일치하지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인 후 이용해주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다."),
    //── Refresh_token ─────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "이용이 제한된 계정입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다. 다시 로그인해주세요."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "액세스 토큰이 유효하지 않습니다."),
    // ── Space ─────────────────────────────────────────
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "존재하지 않는 공간입니다."),
    SPACE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "SPACE_INACTIVE", "예약이 불가능한 공간입니다."),
    INVALID_PRICE_UNIT(HttpStatus.BAD_REQUEST, "INVALID_PRICE_UNIT", "요금은 100원 단위여야 합니다."),
    INVALID_OPERATING_HOURS(HttpStatus.BAD_REQUEST, "INVALID_OPERATING_HOURS", "운영 종료 시각은 시작 시각보다 늦어야 합니다."),

    // ── Reservation ─────────────────────────────────────
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "존재하지 않는 예약입니다."),
    RESERVATION_SLOT_CONFLICT(HttpStatus.CONFLICT, "RESERVATION_SLOT_CONFLICT", "이미 예약된 시간대입니다."),
    RESERVATION_STATE_CONFLICT(HttpStatus.CONFLICT, "RESERVATION_STATE_CONFLICT", "현재 예약 상태에서는 처리할 수 없는 요청입니다."),
    RESERVATION_EXTEND_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "RESERVATION_EXTEND_NOT_ALLOWED", "연장할 수 없는 예약입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "FORBIDDEN_NOT_OWNER", "본인 예약이 아닙니다."),
    SPACE_VERSION_MISMATCH(HttpStatus.CONFLICT, "SPACE_VERSION_MISMATCH", "공간 정보가 변경되었습니다. 다시 확인해주세요."),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", "크레딧 잔액이 부족합니다."),
    INVALID_RESERVATION_TIME(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_TIME", "예약 가능한 시간이 아닙니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다."),

    // ── Door Access ──────────────────────────────────────
    ACCESS_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCESS_TOKEN_NOT_FOUND", "출입 토큰을 찾을 수 없습니다."),
    ACTIVE_ACCESS_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "ACTIVE_ACCESS_TOKEN_NOT_FOUND", "활성 출입 토큰을 찾을 수 없습니다."),
    ACCESS_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCESS_LOG_NOT_FOUND", "출입 기록을 찾을 수 없습니다."),
    ACCESS_DENY_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ACCESS_DENY_REASON_REQUIRED", "출입 거절 사유는 필수입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
