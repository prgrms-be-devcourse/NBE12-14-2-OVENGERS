package com.ovengers.slotkey.notification.repository;

import com.ovengers.slotkey.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 목록, 최신순(id 내림차순). 정렬은 메서드 이름에 고정한다. */
    Page<Notification> findAllByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadAtIsNull(Long memberId);
}
