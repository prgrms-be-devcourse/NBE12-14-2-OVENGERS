package com.ovengers.slotkey.notification.service;

import com.ovengers.slotkey.notification.dto.response.NotificationResponse;
import com.ovengers.slotkey.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 조회. 모든 질의가 memberId 조건을 포함하므로 타인의 알림은 목록에도 개수에도 잡히지 않는다
 * (단건 조회 API가 없어 소유권 예외 경로가 따로 필요 없다).
 */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public NotificationQueryService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Page<NotificationResponse> getMyNotifications(Long memberId, Pageable pageable) {
        return notificationRepository.findAllByMemberIdOrderByIdDesc(memberId, pageable)
                .map(NotificationResponse::from);
    }

    public long countUnread(Long memberId) {
        return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }
}
