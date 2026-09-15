package com.ovengers.slotkey.space.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import lombok.Builder;

import java.time.LocalTime;

@Builder
public record SpaceDetailResponse(
        Long id,
        String name,
        String location,
        String description,
        Integer capacity,
        Long pricePerSlot,
        String imagePath,
        @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        SpaceStatus status) {
    public static SpaceDetailResponse from(Space space) {
        return SpaceDetailResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .location(space.getLocation())
                .description(space.getDescription())
                .capacity(space.getCapacity())
                .pricePerSlot(space.getPricePerSlot())
                .imagePath(space.getImagePath())
                .openingTime(space.getOpeningTime())
                .closingTime(space.getClosingTime())
                .status(space.getStatus())
                .build();
    }
}
