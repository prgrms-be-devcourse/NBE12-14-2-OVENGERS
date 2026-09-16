package com.ovengers.slotkey.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private ObjectMapper objectMapper;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        auditLogService = new AuditLogService(auditLogRepository, objectMapper);
    }

    @Test
    @DisplayName("사유와 전/후 객체가 주어지면 JSON으로 직렬화하여 감사 로그를 저장한다")
    void log_withReasonAndObjects_savesAuditLog() {
        // given
        Long actorMemberId = 1L;
        AuditAction action = AuditAction.MODIFY_SPACE;
        AuditTargetType targetType = AuditTargetType.SPACE;
        Long targetId = 10L;
        String reason = "가격 인상";
        Map<String, Object> before = Map.of("price", 3000);
        Map<String, Object> after = Map.of("price", 5000);

        // when
        auditLogService.log(actorMemberId, action, targetType, targetId, reason, before, after);

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorMemberId()).isEqualTo(actorMemberId);
        assertThat(saved.getAction()).isEqualTo(action);
        assertThat(saved.getTargetType()).isEqualTo(targetType);
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getReason()).isEqualTo(reason);
        assertThat(saved.getBeforeValue()).contains("\"price\":3000");
        assertThat(saved.getAfterValue()).contains("\"price\":5000");
    }

    @Test
    @DisplayName("사유 없이 호출하면 reason이 null로 저장된다")
    void log_withoutReason_savesAuditLogWithNullReason() {
        // given
        Long actorMemberId = 1L;
        AuditAction action = AuditAction.REGISTER_SPACE;
        AuditTargetType targetType = AuditTargetType.SPACE;
        Long targetId = 20L;
        Map<String, Object> after = Map.of("name", "회의실 A");

        // when
        auditLogService.log(actorMemberId, action, targetType, targetId, null, after);

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getBeforeValue()).isNull();
        assertThat(saved.getAfterValue()).contains("\"name\":\"회의실 A\"");
    }

    @Test
    @DisplayName("문자열 타입의 값은 추가 직렬화 없이 그대로 저장된다")
    void log_withStringValues_savesRawString() {
        // given
        String beforeStr = "{\"status\":\"ACTIVE\"}";
        String afterStr = "{\"status\":\"INACTIVE\"}";

        // when
        auditLogService.log(1L, AuditAction.SUSPEND_MEMBER, AuditTargetType.MEMBER, 5L, "규정 위반", beforeStr, afterStr);

        // then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getBeforeValue()).isEqualTo(beforeStr);
        assertThat(saved.getAfterValue()).isEqualTo(afterStr);
    }
}
