package com.ovengers.slotkey.space.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
// SpaceCreateRequest
@Schema(description = "공간 등록 요청")
public record SpaceCreateRequest(

        @Schema(description = "공간 이름 (1~100자)", example = "판교 미팅룸")
        @NotBlank
        @Size(min = 1, max = 100, message = "공간 이름은 1~100자여야 합니다.")
        String name,

        @Schema(description = "공간 위치 또는 주소", example = "경기도 성남시 분당구 판교역로 166")
        @NotBlank(message = "공간 주소를 입력해주세요.")
        String location,

        @Schema(description = "공간 소개", example = "최대 6명이 이용할 수 있는 회의실입니다.")
        String description,

        @Schema(description = "최대 수용 인원 (1명 이상)", example = "6")
        @NotNull(message = "공간 최소 수용 인원을 입력해주세요.")
        @Min(value = 1, message = "공간 최소 수용 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        @Schema(description = "30분당 이용 요금 (100원 이상, 100원 단위)", example = "2500")
        @NotNull(message = "슬롯당 요금을 입력해주세요.")
        @Min(value = 100, message = "공간의 슬롯 당 고정 이용 요금은 100원 이상이어야 합니다.")
        Long pricePerSlot,

        @Schema(
                description = "이미지 주소 또는 경로 (최대 500자). 파일 자체를 전송하지 않습니다.",
                example = "/images/slotkey-test-data/space-01.jpg"
        )
        @Size(max = 500, message = "이미지 경로는 500자 이하여야 합니다.")
        String imagePath,

        @Schema(description = "운영 시작 시각 (HH:mm)", example = "09:00", type = "string")
        @NotNull(message = "공간의 운영 시작 시각을 정해주세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime openingTime,

        @Schema(description = "운영 종료 시각 (HH:mm). 시작 시각보다 늦어야 합니다.", example = "22:00", type = "string")
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
