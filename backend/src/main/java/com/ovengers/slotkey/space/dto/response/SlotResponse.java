package com.ovengers.slotkey.space.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record SlotResponse(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime slotStart,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime slotEnd,

        boolean isAvailable) {
    public static SlotResponse of(LocalDateTime slotStart, LocalDateTime slotEnd, boolean isAvailable) {
        return new SlotResponse(slotStart, slotEnd, isAvailable);
    }
}
