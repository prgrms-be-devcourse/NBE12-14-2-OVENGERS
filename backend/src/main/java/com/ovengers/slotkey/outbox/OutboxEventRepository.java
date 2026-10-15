package com.ovengers.slotkey.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** relay 후보 조회. 잠금 없는 SELECT여야 한다(FOR UPDATE 금지). */
    List<OutboxEvent> findByStatusOrderByIdAsc(String status, Pageable pageable);

    /** 건별 트랜잭션의 PK 조건부 UPDATE. 이미 PUBLISHED면 0을 돌려주고 아무것도 바꾸지 않는다. */
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PUBLISHED', e.publishedAt = :now WHERE e.id = :id AND e.status = 'PENDING'")
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.attempts = e.attempts + 1 WHERE e.id = :id AND e.status = 'PENDING'")
    int increaseAttempts(@Param("id") Long id);
}
