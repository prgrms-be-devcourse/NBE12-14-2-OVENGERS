package com.ovengers.slotkey.space.entity;

import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Long pricePerSlot; // 공간의 슬롯 당 고정 이용 요금

    @Column(length = 500, name = "image_path")
    private String imagePath; // 대표 이미지 저장 경로 (차후 이미지 관련 업로드 기능 필요해질 시 MVC 적용 예정)

    @Column(nullable = false, name = "opening_time")
    private LocalDateTime openingTime; // 운영 시작 시각

    @Column(nullable = false, name = "closing_time")
    private LocalDateTime closingTime; // 운영 종료 시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceStatus status; // 신규 예약 접수 가능 여부

    public void updateDetail(SpaceUpdateRequest request) {
        this.name = request.name();
        this.location = request.location();
        this.description = request.description();
        this.capacity = request.capacity();
        this.pricePerSlot = request.pricePerSlot();
        this.imagePath = request.imagePath();
        this.openingTime = request.openingTime();
        this.closingTime = request.closingTime();
    }

    public void updateStatus(SpaceStatus status) {
        this.status = status;
    }
}
