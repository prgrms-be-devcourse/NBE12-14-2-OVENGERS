package com.ovengers.slotkey.space.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record SpaceUpdateRequest(

        Long targetSpaceId,

        @Size(min = 1, max = 100, message = "공간 이름은 1~100자여야 합니다.")
        String name,

        String location,

        String description,

        @Min(value = 1, message = "공간 최소 수용 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        @Min(value = 100, message = "공간의 슬롯 당 고정 이용 요금은 100원 이상이어야 합니다.")
        Long pricePerSlot,

        @Size(max = 500, message = "이미지 경로는 500자 이하여야 합니다.")
        String imagePath,

        @JsonFormat(pattern = "HH:mm")
        LocalTime openingTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime closingTime,

        SpaceStatus status
) {}
