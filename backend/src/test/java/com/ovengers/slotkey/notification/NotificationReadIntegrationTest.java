package com.ovengers.slotkey.notification;

import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class NotificationReadIntegrationTest extends ReservationIntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Value("${custom.jwt.secret-key}") private String secretKey;

    @Test
    void readsOnlyMyUnreadAndKeepsExistingReadTime() throws Exception {
        Long me = createMember(0);
        Long other = createMember(0);
        insert(me, "mine-1", null);
        insert(me, "mine-2", null);
        LocalDateTime original = LocalDateTime.of(2026, 10, 1, 12, 0);
        insert(me, "read", Timestamp.valueOf(original));
        insert(other, "other", null);
        Long upToId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM notification", Long.class);
        insert(me, "newer", null);
        String token = JwtUtil.createToken(secretKey, 300_000L,
                Map.of("id", me, "email", "read@slotkey.test", "role", "USER"));

        mvc.perform(post("/api/v1/notifications/read-all").param("upToId", upToId.toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id=? AND read_at IS NULL", Integer.class, me)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT read_at FROM notification WHERE member_id=? AND event_id='newer'", Timestamp.class, me)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT read_at FROM notification WHERE event_id='read' AND member_id=?", Timestamp.class, me))
                .isEqualTo(Timestamp.valueOf(original));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id=? AND read_at IS NULL", Integer.class, other)).isEqualTo(1);
        mvc.perform(post("/api/v1/notifications/read-all").param("upToId", upToId.toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(0));
    }

    @Test
    void noTokenReturns401AndLeavesUnread() throws Exception {
        Long me = createMember(0);
        insert(me, "unauth", null);
        mvc.perform(post("/api/v1/notifications/read-all").param("upToId", "1")).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id=? AND read_at IS NULL", Integer.class, me)).isEqualTo(1);
    }

    private void insert(Long memberId, String eventId, Timestamp readAt) {
        jdbcTemplate.update("INSERT INTO notification(member_id,waitlist_entry_id,event_id,message,created_at,read_at) VALUES (?,1,?,'message',NOW(),?)",
                memberId, eventId, readAt);
    }
}
