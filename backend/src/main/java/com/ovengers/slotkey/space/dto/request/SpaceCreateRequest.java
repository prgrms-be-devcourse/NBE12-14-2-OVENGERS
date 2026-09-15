package com.ovengers.slotkey.space.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record SpaceCreateRequest(

        @NotBlank
        @Size(min = 1, max = 100, message = "공간 이름은 1~100자여야 합니다.")
        String name,

        @NotBlank(message = "공간 주소를 입력해주세요.")
        String location,

        String description,

        @NotNull(message = "공간 최소 수용 인원을 입력해주세요.")
        @Min(value = 1, message = "공간 최소 수용 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        @NotNull(message = "슬롯당 요금을 입력해주세요.")
        @Min(value = 100, message = "공간의 슬롯 당 고정 이용 요금은 100원 이상이어야 합니다.")
        Long pricePerSlot,

        @Size(max = 500, message = "이미지 경로는 500자 이하여야 합니다.")
        String imagePath,

        @NotNull(message = "공간의 운영 시작 시각을 정해주세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime openingTime,

        @NotNull(message = "공간의 운영 종료 시각을 정해주세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime closingTime
) {
    public Space toEntity() {
        Space.validateOperatingHours(openingTime, closingTime);
        Space.validatePricePerSlot(pricePerSlot);

        return Space.builder()
                .name(name)
                .location(location)
                .description(description)
                .capacity(capacity)
                .pricePerSlot(pricePerSlot)
                .imagePath(imagePath)
                .openingTime(openingTime)
                .closingTime(closingTime)
                .status(SpaceStatus.ACTIVE)
                .build();
    }
}
