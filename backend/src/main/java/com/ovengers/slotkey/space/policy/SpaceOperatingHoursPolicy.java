package com.ovengers.slotkey.space.policy;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpaceOperatingHoursPolicy {

    public static final int SLOT_DURATION_MINUTES = 30;

    public record SlotWindow(LocalDateTime start, LocalDateTime end) {}

    /**
     * 운영시간(openingTime ~ closingTime)을 30분 단위 슬롯 구간 목록으로 분할 생성합니다.
     */
    public List<SlotWindow> generateSlots(LocalDate date, LocalTime openingTime, LocalTime closingTime) {
        if (openingTime == null
                || closingTime == null
                || !openingTime.isBefore(closingTime)
                || !isMultipleOf30Minutes(openingTime)
                || !isMultipleOf30Minutes(closingTime)) {
            return List.of();
        }

        List<SlotWindow> slots = new ArrayList<>();
        LocalTime currentStart = openingTime;

        while (true) {
            LocalTime currentEnd = currentStart.plusMinutes(SLOT_DURATION_MINUTES);
            // 종료 시각을 넘어서면 중단 (또는 closingTime에 정확히 도달하거나 넘어가는 경우)
            if (currentEnd.isAfter(closingTime) || currentEnd.equals(currentStart)) {
                break;
            }

            LocalDateTime slotStart = date.atTime(currentStart);
            LocalDateTime slotEnd = date.atTime(currentEnd);
            slots.add(new SlotWindow(slotStart, slotEnd));

            if (currentEnd.equals(closingTime)) {
                break;
            }
            currentStart = currentEnd;
        }

        return slots;
    }

    /**
     * 요청된 시간 구간이 공간의 운영시간 내에 온전히 포함되는지 검증합니다.
     */
    public boolean isWithinOperatingHours(
            LocalTime openingTime,
            LocalTime closingTime,
            LocalTime requestedStart,
            LocalTime requestedEnd
    ) {
        if (openingTime == null || closingTime == null || requestedStart == null || requestedEnd == null) {
            return false;
        }
        if (!requestedStart.isBefore(requestedEnd)) {
            return false;
        }
        return !requestedStart.isBefore(openingTime) && !requestedEnd.isAfter(closingTime);
    }

    /**
     * 시간이 30분 단위 배수인지 검증합니다. (0분 또는 30분, 초/나노초는 0)
     */
    public boolean isMultipleOf30Minutes(LocalTime time) {
        if (time == null) {
            return false;
        }
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
