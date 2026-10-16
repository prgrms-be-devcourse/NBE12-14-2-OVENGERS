package com.ovengers.slotkey.notification.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.global.security.CurrentMember;
import com.ovengers.slotkey.notification.dto.response.NotificationResponse;
import com.ovengers.slotkey.notification.dto.response.UnreadCountResponse;
import com.ovengers.slotkey.notification.service.NotificationQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import com.ovengers.slotkey.notification.dto.response.ReadAllResponse;

/**
 * 내 알림 조회 API(4단계). 인증은 SecurityConfig의 anyRequest().authenticated()가 강제하고,
 * 항상 토큰의 회원 id로만 조회·읽음 처리하므로 타인의 알림은 건드리지 않는다.
 *
 * 신규 코드이므로 Lombok을 쓰지 않는다(AGENTS.md).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @CurrentMember AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(
                notificationQueryService.getMyNotifications(principal.memberId(), pageable))));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @CurrentMember AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                new UnreadCountResponse(notificationQueryService.countUnread(principal.memberId()))));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<ReadAllResponse>> readAll(@CurrentMember AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(new ReadAllResponse(
                notificationQueryService.readAll(principal.memberId()))));
    }
}
