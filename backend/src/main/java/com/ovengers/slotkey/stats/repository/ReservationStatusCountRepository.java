package com.ovengers.slotkey.stats.repository;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/** 이용 시작 시각이 [from, to)에 속한 예약의 상태별 건수. spaceId가 null이면 전체 공간. */
@Repository
public class ReservationStatusCountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReservationStatusCountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<ReservationStatus, Long> countByStatus(LocalDateTime from, LocalDateTime to, Long spaceId) {
        Map<ReservationStatus, Long> counts = new EnumMap<>(ReservationStatus.class);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("spaceId", spaceId);
        jdbc.query("""
                SELECT status, COUNT(*) AS cnt
                FROM reservation
                WHERE start_time >= :from AND start_time < :to
                  AND (:spaceId IS NULL OR space_id = :spaceId)
                GROUP BY status
                """, params, rs -> {
            counts.put(ReservationStatus.valueOf(rs.getString("status")), rs.getLong("cnt"));
        });
        return counts;
    }
}
