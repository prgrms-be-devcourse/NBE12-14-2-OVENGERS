package com.ovengers.slotkey.space.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 공간 및 날짜에 대해 이미 점유된 슬롯의 시작 시각 목록을 제공하는 인터페이스.
 * 예약 도메인과의 느슨한 결합을 위한 포트 역할을 합니다.
 */
public interface OccupiedSlotProvider {

    Set<LocalDateTime> getOccupiedSlotStarts(Long spaceId, LocalDate date, LocalDateTime now);
}
