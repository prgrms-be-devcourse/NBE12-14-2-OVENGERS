package com.ovengers.slotkey.notification.repository;

import com.ovengers.slotkey.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 목록, 최신순(id 내림차순). 정렬은 메서드 이름에 고정한다. */
    Page<Notification> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadAtIsNull(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.memberId = :memberId AND n.readAt IS NULL AND n.id <= :upToId")
    int markAllRead(@Param("memberId") Long memberId, @Param("upToId") Long upToId, @Param("readAt") LocalDateTime readAt);
}
