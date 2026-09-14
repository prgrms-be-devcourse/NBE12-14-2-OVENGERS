package com.ovengers.slotkey.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Table(name = "audit_logs")
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog { // 공간 등록·수정과 일반 회원 정지·복구 등 관리 기능의 처리 이력을 기록
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK, 감사 로그 고유 식별자

    // @ManyToOne
    // private Member actorMember; // FK -> member.id, 관리자 작업 수행 회원. 시스템 작업인 경우 NULL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action; // 관리자가 수행한 작업의 종류

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "target_type")
    private AuditTargetType targetType; // 작업 대상 유형

    @Column(nullable = false, name = "target_id")
    private Long targetId; // 작업 대상의 식별자 (FK x. 대상이 공간 ID일 수도 있고, 회원 ID일 수도 있음)

    @Column(length = 500)
    private String reason; // 관리자 작업 사유. 사유없는 작업은 NULL

    @Column(columnDefinition = "TEXT", name = "before_value")
    private String beforeValue; // 변경 전 데이터

    @Column(columnDefinition = "TEXT", name = "after_value")
    private String afterValue; // 변경 후 데이터. 어떤 데이터가 변경되었는지 추적할 수 있도록 변경 전/후 데이터를 기록

    @CreatedDate
    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt; // 관리자 작업이 수행된 시각
}
