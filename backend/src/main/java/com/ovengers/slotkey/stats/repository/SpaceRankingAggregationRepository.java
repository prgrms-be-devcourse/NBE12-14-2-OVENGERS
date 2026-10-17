package com.ovengers.slotkey.stats.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공간별 순매출·예약 건수·슬롯 수 집계. 범위는 모두 [from, to) 반열린 구간이다.
 * 취소는 reservation_slot 행을 지우므로 슬롯 테이블이 아니라 reservation과 크레딧 원장으로 센다.
 */
@Repository
public class SpaceRankingAggregationRepository {

    // 원장 부호는 회원 기준(차감 -, 환급 +)이라 공간 매출은 -SUM(amount). 위약금(PENALTY)도 수익이다.
    private static final String REVENUE_SQL = """
            SELECT r.space_id AS space_id, -SUM(t.amount) AS net_revenue
            FROM credit_transaction t
            JOIN reservation r ON r.id = t.reservation_id
            WHERE t.type IN ('RESERVATION_CHARGE', 'REFUND', 'PENALTY')
              AND t.created_at >= :from AND t.created_at < :to
            GROUP BY r.space_id
            """;

    // 이용 시작 시각 기준. 시작 후에는 COMPLETED/NO_SHOW로만 가므로 기간 종료 시점에 상태가 확정되어 있다.
    private static final String COUNT_SQL = """
            SELECT space_id,
                   COUNT(*) AS reservation_count,
                   SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time) DIV 30) AS slot_count
            FROM reservation
            WHERE status IN ('CONFIRMED', 'IN_USE', 'COMPLETED', 'NO_SHOW')
              AND start_time >= :from AND start_time < :to
            GROUP BY space_id
            """;

    private static final String LOCATION_SQL = "SELECT id, location FROM spaces WHERE id IN (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    public SpaceRankingAggregationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 세 지표가 모두 0인 공간은 포함하지 않는다. 비활성 공간도 실적이 있으면 포함한다. */
    public List<SpaceRankingAggregate> aggregate(LocalDateTime from, LocalDateTime to) {
        MapSqlParameterSource range = new MapSqlParameterSource().addValue("from", from).addValue("to", to);

        Map<Long, Long> revenueBySpace = new HashMap<>();
        jdbc.query(REVENUE_SQL, range, rs -> {
            revenueBySpace.put(rs.getLong("space_id"), rs.getLong("net_revenue"));
        });

        Map<Long, int[]> countsBySpace = new HashMap<>();
        jdbc.query(COUNT_SQL, range, rs -> {
            countsBySpace.put(rs.getLong("space_id"),
                    new int[]{rs.getInt("reservation_count"), rs.getInt("slot_count")});
        });

        Set<Long> spaceIds = new HashSet<>(revenueBySpace.keySet());
        spaceIds.addAll(countsBySpace.keySet());
        if (spaceIds.isEmpty()) {
            return List.of();
        }

        Map<Long, String> locationBySpace = new HashMap<>();
        jdbc.query(LOCATION_SQL, new MapSqlParameterSource("ids", spaceIds), rs -> {
            locationBySpace.put(rs.getLong("id"), rs.getString("location"));
        });

        List<SpaceRankingAggregate> result = new ArrayList<>();
        for (Long spaceId : spaceIds) {
            int[] counts = countsBySpace.getOrDefault(spaceId, new int[]{0, 0});
            SpaceRankingAggregate aggregate = new SpaceRankingAggregate(
                    spaceId,
                    locationBySpace.get(spaceId),
                    revenueBySpace.getOrDefault(spaceId, 0L),
                    counts[0],
                    counts[1]);
            if (!aggregate.isAllZero()) {
                result.add(aggregate);
            }
        }
        return result;
    }
}
