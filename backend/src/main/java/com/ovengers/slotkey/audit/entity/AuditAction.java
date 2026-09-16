package com.ovengers.slotkey.audit.entity;

public enum AuditAction {
    REGISTER_SPACE, // 공간 등록
    MODIFY_SPACE, // 공간 수정
    SUSPEND_MEMBER, // 계정 정지
    REACTIVATE_MEMBER, // 계정 복구
    FORCE_CANCEL_RESERVATION, // 관리자 예약 강제 취소
    GRANT_CREDIT // 관리자 크레딧 지급
}
