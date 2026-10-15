package com.ovengers.slotkey.notification.consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 컨슈머 멱등 기록(processed_event). 호출자 트랜잭션 안에서 INSERT하므로 알림 저장이 롤백되면 이 기록도 사라진다.
 *
 * `INSERT IGNORE`를 쓰는 이유: 중복 키를 예외로 받으면 호출자 트랜잭션이 rollback-only로 오염되므로,
 * 영향 행 수(1=처음, 0=이미 처리함)로 판정한다. UNIQUE(consumer, event_id)가 동시 중복도 직렬화한다 —
 * 두 번째 INSERT는 첫 트랜잭션이 끝날 때까지 기다린 뒤 0을 받는다.
 */
@Component
public class ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ProcessedEventStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 처음 보는 이벤트면 true, 이미 처리한 이벤트면 false. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumer, String eventId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO processed_event (consumer, event_id, processed_at) VALUES (?, ?, ?)",
                consumer, eventId, LocalDateTime.now(clock)) == 1;
    }
}
