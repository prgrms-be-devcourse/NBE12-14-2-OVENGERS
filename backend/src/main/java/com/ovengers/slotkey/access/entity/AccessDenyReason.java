package com.ovengers.slotkey.access.entity;

public enum AccessDenyReason {
    TOKEN_NOT_FOUND,
    TOKEN_REVOKED,
    RESERVATION_NOT_ACTIVE,
    OUTSIDE_ALLOWED_TIME,
    SPACE_MISMATCH,
    MEMBER_MISMATCH
}