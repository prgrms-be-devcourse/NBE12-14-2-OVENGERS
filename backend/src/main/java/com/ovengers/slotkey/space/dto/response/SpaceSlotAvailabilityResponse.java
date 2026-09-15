package com.ovengers.slotkey.space.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record SpaceSlotAvailabilityResponse(
        Long spaceId,

        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,

        List<SlotResponse> slots) {
    public static SpaceSlotAvailabilityResponse of(Long spaceId, LocalDate date, List<SlotResponse> slots) {
        return new SpaceSlotAvailabilityResponse(spaceId, date, slots);
    }
}
