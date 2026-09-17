package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class AdminSpaceServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AdminSpaceService adminSpaceService;

    @Autowired
    private SpaceRepository spaceRepository;

    @SpyBean
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
        spaceRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("이름과 가격 변경을 포함한 공간 수정 요청이 무효한 운영시간으로 실패하면 롤백되어 DB의 원래 값이 유지된다")
    void updateSpace_invalidOperatingHours_rollsBack_andPreservesOriginalSpace() {
        // given
        Space space = spaceRepository.save(Space.builder()
                .name("원본 회의실")
                .location("테헤란로 100")
                .description("설명")
                .capacity(4)
                .pricePerSlot(3000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(18, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());

        Long spaceId = space.getId();

        // 이름과 가격을 바꾸면서 운영시간을 무효하게 전달 (22:00 ~ 08:00)
        SpaceUpdateRequest invalidRequest = new SpaceUpdateRequest(
                null,
                "수정 시도 회의실",
                "수정 위치",
                "수정 설명",
                8,
                5000L,
                "/new.jpg",
                LocalTime.of(22, 0),
                LocalTime.of(8, 0),
                SpaceStatus.INACTIVE
        );

        // when & then
        assertThatThrownBy(() -> adminSpaceService.updateSpace(spaceId, invalidRequest, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

        // DB 재조회하여 원래 상태가 온전히 보존되었는지 검증 (name, pricePerSlot, version, hours 등)
        Space reloaded = spaceRepository.findById(spaceId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("원본 회의실");
        assertThat(reloaded.getPricePerSlot()).isEqualTo(3000L);
        assertThat(reloaded.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(reloaded.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(reloaded.getVersion()).isEqualTo(0);

        // 감사 로그도 저장되지 않아야 함
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("공간 등록 도중 감사 로그 저장이 실패하면 공간 엔티티도 저장되지 않고 롤백된다")
    void createSpace_auditLogFails_rollsBack_spaceNotSaved() {
        // given
        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        SpaceCreateRequest request = new SpaceCreateRequest(
                "신규 회의실",
                "강남대로 200",
                "설명",
                6,
                4000L,
                null,
                LocalTime.of(9, 0),
                LocalTime.of(21, 0)
        );

        // when & then
        assertThatThrownBy(() -> adminSpaceService.createSpace(request, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // 감사 실패로 공간도 롤백되어 DB에 남지 않음
        assertThat(spaceRepository.count()).isZero();
    }

    @Test
    @DisplayName("공간 수정 도중 감사 로그 저장이 실패하면 공간 변경사항이 롤백되어 원래 값을 유지한다")
    void updateSpace_auditLogFails_rollsBack_spaceNotUpdated() {
        // given
        Space space = spaceRepository.save(Space.builder()
                .name("수정 전 공간")
                .location("위치")
                .capacity(4)
                .pricePerSlot(3000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(18, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());

        Long spaceId = space.getId();

        doThrow(new RuntimeException("Audit DB failure"))
                .when(auditLogRepository).save(any());

        SpaceUpdateRequest updateRequest = new SpaceUpdateRequest(
                null,
                "수정 후 공간",
                null,
                null,
                null,
                6000L,
                null,
                null,
                null,
                null
        );

        // when & then
        assertThatThrownBy(() -> adminSpaceService.updateSpace(spaceId, updateRequest, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit DB failure");

        // DB 재조회하여 공간이 수정 전 상태와 version을 유지하는지 검증
        Space reloaded = spaceRepository.findById(spaceId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("수정 전 공간");
        assertThat(reloaded.getPricePerSlot()).isEqualTo(3000L);
        assertThat(reloaded.getVersion()).isEqualTo(0);
    }
}

