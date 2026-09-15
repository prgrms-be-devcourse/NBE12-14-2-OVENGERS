package com.ovengers.slotkey.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Table(name = "audit_logs")
@Entity
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK, 감사 로그 고유 식별자

    @Column(name = "actor_member_id")
    private Long actorMemberId; // 관리자 작업 수행 회원 ID. 시스템 작업인 경우 NULL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action; // 관리자가 수행한 작업의 종류

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "target_type")
    private AuditTargetType targetType; // 작업 대상 유형

    @Column(nullable = false, name = "target_id")
    private Long targetId; // 작업 대상의 식별자

    @Column(length = 500)
    private String reason; // 관리자 작업 사유. 사유 없는 작업은 NULL

    @Column(columnDefinition = "TEXT", name = "before_value")
    private String beforeValue; // 변경 전 데이터 (JSON)

    @Column(columnDefinition = "TEXT", name = "after_value")
    private String afterValue; // 변경 후 데이터 (JSON)

    @CreatedDate
    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt; // 관리자 작업이 수행된 시각

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
