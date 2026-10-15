package com.ovengers.slotkey.notification;

import com.ovengers.slotkey.notification.dto.response.NotificationResponse;
import com.ovengers.slotkey.notification.service.NotificationQueryService;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 알림 조회: 최신순·페이징·본인 것만·안 읽은 개수. */
class NotificationQueryIntegrationTest extends ReservationIntegrationTestSupport {

    @Autowired
    private NotificationQueryService service;

    @Test
    @DisplayName("내 알림만 최신순으로 페이징해 돌려주고, 타인의 알림은 보이지 않는다")
    void getMyNotifications_onlyMine_newestFirst_paged() {
        Long me = createMember(0);
        Long other = createMember(0);
        long first = insert(me, 1L, "e1", "첫번째");
        long second = insert(me, 2L, "e2", "두번째");
        long third = insert(me, 3L, "e3", "세번째");
        insert(other, 4L, "e4", "남의 것");

        Page<NotificationResponse> page0 = service.getMyNotifications(me, PageRequest.of(0, 2));
        Page<NotificationResponse> page1 = service.getMyNotifications(me, PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getContent()).extracting(NotificationResponse::id).containsExactly(third, second);
        assertThat(page1.getContent()).extracting(NotificationResponse::id).containsExactly(first);
        assertThat(page0.getContent()).extracting(NotificationResponse::message).doesNotContain("남의 것");
    }

    @Test
    @DisplayName("안 읽은 개수는 내 알림 중 read_at이 비어 있는 것만 센다")
    void countUnread_countsOnlyMyUnread() {
        Long me = createMember(0);
        Long other = createMember(0);
        insert(me, 1L, "e1", "a");
        long read = insert(me, 2L, "e2", "b");
        insert(other, 3L, "e3", "c");
        jdbcTemplate.update("UPDATE notification SET read_at = NOW() WHERE id = ?", read);

        assertThat(service.countUnread(me)).isEqualTo(1);
        assertThat(service.countUnread(createMember(0))).isZero();
    }

    private long insert(Long memberId, Long entryId, String eventId, String message) {
        jdbcTemplate.update("INSERT INTO notification (member_id, waitlist_entry_id, event_id, message, created_at) "
                + "VALUES (?, ?, ?, ?, NOW())", memberId, entryId, eventId, message);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM notification WHERE member_id = ?", Long.class, memberId);
    }
}
