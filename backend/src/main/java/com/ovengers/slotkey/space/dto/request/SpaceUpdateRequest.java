package com.ovengers.slotkey.space.dto.request;

import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SpaceUpdateRequest(

        @NotNull(message = "수정할 공간을 선택해주세요.")
        Long targetSpaceId,

        @NotBlank
        @Size(min = 1, max = 100, message = "공간 이름은 1~100자여야 합니다.")
        String name,

        @NotBlank
        String location,

        String description,

        @Size(min = 1, message = "공간 최소 수용 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        @Size(min = 100, message = "공간의 슬롯 당 고정 이용 요금은 100원 이상이어야 합니다.")
        Long pricePerSlot,

        @Size(max = 500, message = "이미지 경로는 500자 이하여야 합니다.")
        String imagePath,

        @NotNull(message = "공간의 운영 시작 시각을 정해주세요.")
        LocalDateTime openingTime,

        @NotNull(message = "공간의 운영 종료 시각을 정해주세요.")
        LocalDateTime closingTime
) {}
