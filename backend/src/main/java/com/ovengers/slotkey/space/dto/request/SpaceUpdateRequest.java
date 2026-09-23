package com.ovengers.slotkey.space.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
// SpaceUpdateRequest
@Schema(description = "공간 수정 요청. null인 항목은 기존 값을 유지합니다.")
public record SpaceUpdateRequest(

        @Schema(description = "수정 대상 지정에 사용하지 않는 필드입니다. 대상 ID는 URL의 spaceId로 전달합니다.")
        Long targetSpaceId,

        @Schema(description = "공간 이름 (1~100자)", example = "판교 미팅룸")
        @Size(min = 1, max = 100, message = "공간 이름은 1~100자여야 합니다.")
        String name,

        @Schema(description = "공간 위치 또는 주소", example = "경기도 성남시 분당구 판교역로 166")
        String location,

        @Schema(description = "공간 소개", example = "최대 6명이 이용할 수 있는 회의실입니다.")
        String description,

        @Schema(description = "최대 수용 인원 (1명 이상)", example = "6")
        @Min(value = 1, message = "공간 최소 수용 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        @Schema(description = "30분당 이용 요금 (100원 이상, 100원 단위)", example = "2500")
        @Min(value = 100, message = "공간의 슬롯 당 고정 이용 요금은 100원 이상이어야 합니다.")
        Long pricePerSlot,

        @Schema(
                description = "이미지 주소 또는 경로 (최대 500자). 파일 자체를 전송하지 않습니다.",
                example = "/images/slotkey-test-data/space-01.jpg"
        )
        @Size(max = 500, message = "이미지 경로는 500자 이하여야 합니다.")
        String imagePath,

        @Schema(description = "운영 시작 시각 (HH:mm)", example = "09:00", type = "string")
        @JsonFormat(pattern = "HH:mm")
        LocalTime openingTime,

        @Schema(description = "운영 종료 시각 (HH:mm). 시작 시각보다 늦어야 합니다.", example = "22:00", type = "string")
        @JsonFormat(pattern = "HH:mm")
        LocalTime closingTime,

        @Schema(description = "변경할 공간 상태", example = "ACTIVE")
        SpaceStatus status
) {}
