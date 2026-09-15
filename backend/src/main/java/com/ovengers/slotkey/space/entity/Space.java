package com.ovengers.slotkey.space.entity;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "spaces")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK, 고유 식별자

    @Column(nullable = false, length = 100)
    private String name; // 공간 이름

    @Column(nullable = false)
    private String location; // 공간의 위치 or 지역명

    @Column(columnDefinition = "TEXT")
    private String description; // 공간 상세 소개

    @Column(nullable = false)
    private Integer capacity; // 공간 최대 수용 인원

    @Column(nullable = false, name = "price_per_slot")
    private Long pricePerSlot; // 공간의 슬롯 당 고정 이용 요금 (100원 단위)

    @Column(length = 500, name = "image_path")
    private String imagePath; // 대표 이미지 저장 경로

    @Column(nullable = false, name = "opening_time")
    private LocalTime openingTime; // 일일 운영 시작 시각

    @Column(nullable = false, name = "closing_time")
    private LocalTime closingTime; // 일일 운영 종료 시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceStatus status; // 신규 예약 접수 가능 여부

    public static void validateOperatingHours(LocalTime openingTime, LocalTime closingTime) {
        if (openingTime == null || closingTime == null || !openingTime.isBefore(closingTime)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATING_HOURS);
        }
    }

    public static void validatePricePerSlot(Long pricePerSlot) {
        if (pricePerSlot == null || pricePerSlot <= 0 || pricePerSlot % 100 != 0) {
            throw new BusinessException(ErrorCode.INVALID_PRICE_UNIT);
        }
    }

    public void updateDetail(SpaceUpdateRequest request) {
        if (request.name() != null) {
            this.name = request.name();
        }
        if (request.location() != null) {
            this.location = request.location();
        }
        if (request.description() != null) {
            this.description = request.description();
        }
        if (request.capacity() != null) {
            this.capacity = request.capacity();
        }
        if (request.pricePerSlot() != null) {
            validatePricePerSlot(request.pricePerSlot());
            this.pricePerSlot = request.pricePerSlot();
        }
        if (request.imagePath() != null) {
            this.imagePath = request.imagePath();
        }
        if (request.openingTime() != null && request.closingTime() != null) {
            validateOperatingHours(request.openingTime(), request.closingTime());
            this.openingTime = request.openingTime();
            this.closingTime = request.closingTime();
        } else if (request.openingTime() != null) {
            validateOperatingHours(request.openingTime(), this.closingTime);
            this.openingTime = request.openingTime();
        } else if (request.closingTime() != null) {
            validateOperatingHours(this.openingTime, request.closingTime());
            this.closingTime = request.closingTime();
        }
        if (request.status() != null) {
            this.status = request.status();
        }
    }

    public void updateStatus(SpaceStatus status) {
        this.status = status;
    }
}
