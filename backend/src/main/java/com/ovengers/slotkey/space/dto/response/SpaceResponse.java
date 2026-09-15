package com.ovengers.slotkey.space.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalTime;

@Builder(access = AccessLevel.PROTECTED)
public record SpaceResponse(
        Long id,
        String name,
        String location,
        Integer capacity,
        Long pricePerSlot,
        String imagePath,
        @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        SpaceStatus status) {
    public static SpaceResponse from(Space space) {
        return SpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .location(space.getLocation())
                .capacity(space.getCapacity())
                .pricePerSlot(space.getPricePerSlot())
                .imagePath(space.getImagePath())
                .openingTime(space.getOpeningTime())
                .closingTime(space.getClosingTime())
                .status(space.getStatus())
                .build();
    }
}
