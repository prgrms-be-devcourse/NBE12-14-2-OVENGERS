package com.ovengers.slotkey.space.entity;

import java.util.Arrays;

/**
 * 공간 지역. 별도 region 테이블 없이 {@code spaces.location}에 {@link #label}을 그대로 저장하고,
 * 등록·수정 요청에서만 이 enum으로 검증한다(DB 제약 아님). 근거: docs/decisions/space-ranking.md §2-5.
 */
public enum SpaceRegion {
    PANGYO("판교"),
    GANGNAM("강남"),
    HANAM("하남");

    private final String label;

    SpaceRegion(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isSupported(String location) {
        return location != null && Arrays.stream(values()).anyMatch(region -> region.label.equals(location));
    }
}
