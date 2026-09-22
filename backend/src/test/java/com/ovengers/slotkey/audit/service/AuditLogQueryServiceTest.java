package com.ovengers.slotkey.audit.service;

import com.ovengers.slotkey.audit.dto.AdminAuditLogSearchCondition;
import com.ovengers.slotkey.audit.dto.AuditLogResponse;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogQueryService auditLogQueryService;

    @Test
    @DisplayName("dateFrom과 dateTo가 전달되면 KST 기준 [dateFrom 00:00:00, dateTo+1일 00:00:00) 반개구간으로 변환된다")
    void searchAuditLogs_halfOpenInterval_conversion() {
        // given
        AdminAuditLogSearchCondition condition = new AdminAuditLogSearchCondition(
                null, null, null, null,
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 22)
        );
        Pageable requestPageable = PageRequest.of(0, 10);
        given(auditLogRepository.searchAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Page.empty());

        // when
        auditLogQueryService.searchAuditLogs(condition, requestPageable);

        // then
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(auditLogRepository).searchAuditLogs(
                eq(null), eq(null), eq(null), eq(null),
                fromCaptor.capture(),
                toCaptor.capture(),
                any(Pageable.class)
        );

        assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0, 0));
        assertThat(toCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 23, 0, 0, 0));
    }

    @Test
    @DisplayName("dateFrom이 dateTo보다 늦으면 VALIDATION_FAILED 예외를 던지고 repository를 호출하지 않는다")
    void searchAuditLogs_dateFromAfterDateTo_throwsValidationFailed() {
        // given
        AdminAuditLogSearchCondition condition = new AdminAuditLogSearchCondition(
                null, null, null, null,
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 20)
        );
        Pageable requestPageable = PageRequest.of(0, 10);

        // when & then
        assertThatThrownBy(() -> auditLogQueryService.searchAuditLogs(condition, requestPageable))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("요청의 sort 파라미터를 무시하고 서버 고정 createdAt DESC, id DESC 정렬로 재구성하여 repository에 전달한다")
    void searchAuditLogs_ignoresClientSort_andForcesServerSort() {
        // given
        AdminAuditLogSearchCondition condition = new AdminAuditLogSearchCondition(
                1L, AuditAction.MODIFY_SPACE, AuditTargetType.SPACE, 10L, null, null
        );
        // 클라이언트가 임의의 정렬(예: targetId ASC)을 요청한 경우
        Pageable clientPageable = PageRequest.of(1, 15, Sort.by(Sort.Direction.ASC, "targetId"));
        given(auditLogRepository.searchAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Page.empty());

        // when
        auditLogQueryService.searchAuditLogs(condition, clientPageable);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).searchAuditLogs(
                eq(1L), eq(AuditAction.MODIFY_SPACE), eq(AuditTargetType.SPACE), eq(10L),
                eq(null), eq(null), pageableCaptor.capture()
        );

        Pageable passedPageable = pageableCaptor.getValue();
        assertThat(passedPageable.getPageNumber()).isEqualTo(1);
        assertThat(passedPageable.getPageSize()).isEqualTo(15);
        assertThat(passedPageable.getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    @Test
    @DisplayName("AuditLog 엔티티의 모든 필드(nullable 포함, JSON 원본 문자열 보존)가 AuditLogResponse로 매핑된다")
    void searchAuditLogs_mapsAllFieldsCorrectly() {
        // given
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 20, 14, 30, 0);
        AuditLog auditLog = AuditLog.builder()
                .id(100L)
                .actorMemberId(null) // nullable 시스템 작업
                .action(AuditAction.FORCE_CANCEL_RESERVATION)
                .targetType(AuditTargetType.RESERVATION)
                .targetId(200L)
                .reason(null) // nullable 사유
                .beforeValue("{\"status\":\"CONFIRMED\"}")
                .afterValue("{\"status\":\"CANCELLED\"}")
                .createdAt(createdAt)
                .build();

        Page<AuditLog> entityPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
        given(auditLogRepository.searchAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(entityPage);

        // when
        Page<AuditLogResponse> responsePage =
                auditLogQueryService.searchAuditLogs(null, PageRequest.of(0, 10));

        // then
        assertThat(responsePage.getContent()).hasSize(1);
        AuditLogResponse response = responsePage.getContent().get(0);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.actorMemberId()).isNull();
        assertThat(response.action()).isEqualTo(AuditAction.FORCE_CANCEL_RESERVATION);
        assertThat(response.targetType()).isEqualTo(AuditTargetType.RESERVATION);
        assertThat(response.targetId()).isEqualTo(200L);
        assertThat(response.reason()).isNull();
        assertThat(response.beforeValue()).isEqualTo("{\"status\":\"CONFIRMED\"}");
        assertThat(response.afterValue()).isEqualTo("{\"status\":\"CANCELLED\"}");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
