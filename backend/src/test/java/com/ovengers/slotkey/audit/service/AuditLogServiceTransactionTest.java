package com.ovengers.slotkey.audit.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.support.FixedClockConfig;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(FixedClockConfig.class)
class AuditLogServiceTransactionTest extends IntegrationTestSupport {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("트랜잭션 없이 호출하면 IllegalTransactionStateException이 발생하고 감사 로그가 저장되지 않는다")
    void log_withoutTransaction_throwsIllegalTransactionStateException_andDoesNotSave() {
        // when & then
        assertThatThrownBy(() -> auditLogService.log(
                1L,
                AuditAction.REGISTER_SPACE,
                AuditTargetType.SPACE,
                10L,
                "트랜잭션 없는 호출",
                null,
                Map.of("name", "회의실 A"))).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("트랜잭션 내부에서 호출하면 감사 로그가 정상 저장되고 고정된 시각(createdAt)으로 감사 기록된다")
    void log_withinTransaction_savesAuditLogWithFixedCreatedAt() {
        // given
        Long actorMemberId = 1L;
        AuditAction action = AuditAction.REGISTER_SPACE;
        AuditTargetType targetType = AuditTargetType.SPACE;
        Long targetId = 10L;
        String reason = "신규 공간 등록";
        Map<String, Object> after = Map.of("name", "회의실 A", "price", 3000);

        // when
        transactionTemplate.executeWithoutResult(status -> auditLogService.log(
                actorMemberId,
                action,
                targetType,
                targetId,
                reason,
                null,
                after));

        // then
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);

        AuditLog saved = auditLogs.get(0);
        assertThat(saved.getActorMemberId()).isEqualTo(actorMemberId);
        assertThat(saved.getAction()).isEqualTo(action);
        assertThat(saved.getTargetType()).isEqualTo(targetType);
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getReason()).isEqualTo(reason);
        assertThat(saved.getBeforeValue()).isNull();
        assertThat(saved.getAfterValue()).contains("\"name\":\"회의실 A\"");
        assertThat(saved.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_DATE_TIME);
    }
}
