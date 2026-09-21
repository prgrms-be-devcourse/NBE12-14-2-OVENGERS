package com.ovengers.slotkey.support;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 실제 MySQL(Testcontainers) 위에서 예약·크레딧·출입 흐름의 동시성/롤백을 검증하는 통합 테스트의 공통 기반.
 * 테스트가 만든 회원·공간·예약 데이터는 테스트가 끝나면 이 클래스가 FK 순서대로 정리한다.
 * Docker가 필요하므로 로컬에서 Docker Desktop을 켠 뒤 실행한다.
 */
public abstract class ReservationIntegrationTestSupport extends IntegrationTestSupport {

    protected static final int PRICE_PER_SLOT = 5000;

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected SpaceRepository spaceRepository;
    @Autowired
    protected ReservationRepository reservationRepository;
    @Autowired
    protected ReservationSlotRepository reservationSlotRepository;
    @Autowired
    protected Clock clock;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> spaceIds = new ArrayList<>();

    @AfterEach
    protected void cleanUpFixtures() {
        String members = inList(memberIds);
        String spaces = inList(spaceIds);
        String reservationsOfSpaces = "SELECT id FROM reservation WHERE space_id IN (" + spaces + ")";

        jdbcTemplate.update("DELETE FROM door_access_log WHERE actor_member_id IN (" + members
                + ") OR reservation_id IN (" + reservationsOfSpaces + ")");
        jdbcTemplate.update("DELETE FROM door_access_token WHERE reservation_id IN (" + reservationsOfSpaces + ")");
        jdbcTemplate.update("DELETE FROM credit_transaction WHERE member_id IN (" + members + ")");
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE member_id IN (" + members + ")");
        jdbcTemplate.update("DELETE FROM reservation_status_history WHERE changed_by_member_id IN (" + members
                + ") OR reservation_id IN (" + reservationsOfSpaces + ")");
        jdbcTemplate.update("DELETE FROM reservation_slot WHERE space_id IN (" + spaces + ")");
        jdbcTemplate.update("DELETE FROM reservation WHERE space_id IN (" + spaces + ") OR member_id IN (" + members + ")");
        jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_member_id IN (" + members + ")");
        jdbcTemplate.update("DELETE FROM spaces WHERE id IN (" + spaces + ")");
        jdbcTemplate.update("DELETE FROM member WHERE id IN (" + members + ")");
    }

    // ---------- 데이터 준비 ----------

    /** 잔액이 balance인 활성 회원을 만든다. */
    protected Long createMember(int balance) {
        Member member = memberRepository.save(new Member(
                "fixture-" + System.nanoTime() + "-" + memberIds.size() + "@slotkey.test",
                "{noop}password",
                "fixture"));
        memberIds.add(member.getId());
        setBalance(member.getId(), balance);
        return member.getId();
    }

    /** 30분당 PRICE_PER_SLOT원, 09:00~22:00 운영하는 활성 공간을 만든다. */
    protected Long createSpace() {
        Space space = spaceRepository.save(Space.builder()
                .name("fixture-space-" + System.nanoTime())
                .location("서울")
                .capacity(10)
                .pricePerSlot((long) PRICE_PER_SLOT)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());
        spaceIds.add(space.getId());
        return space.getId();
    }

    /**
     * 예약 행과 [start, end)의 30분 슬롯 행을 직접 저장한다(서비스의 시간 정책을 거치지 않는다).
     * HELD면 홀드 만료 시각을 지금 + 10분으로 둔다. 금액은 PRICE_PER_SLOT × 슬롯 수다.
     */
    protected Long createReservation(Long memberId, Long spaceId, ReservationStatus status,
                                     LocalDateTime start, LocalDateTime end) {
        int slotCount = (int) (Duration.between(start, end).toMinutes() / 30);
        LocalDateTime now = now();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .memberId(memberId)
                .spaceId(spaceId)
                .startTime(start)
                .endTime(end)
                .status(status)
                .pricePerSlotSnapshot(PRICE_PER_SLOT)
                .totalAmount(PRICE_PER_SLOT * slotCount)
                .holdExpiresAt(status == ReservationStatus.HELD ? now.plusMinutes(10) : null)
                .createdAt(now)
                .build());
        for (LocalDateTime slot = start; slot.isBefore(end); slot = slot.plusMinutes(30)) {
            reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), spaceId, slot));
        }
        return reservation.getId();
    }

    protected LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    /** 서비스와 같은 Clock 기준 내일의 hour:minute. */
    protected LocalDateTime tomorrowAt(int hour, int minute) {
        return now().plusDays(1).withHour(hour).withMinute(minute).withSecond(0);
    }

    protected void setBalance(Long memberId, int balance) {
        jdbcTemplate.update("UPDATE member SET balance = ? WHERE id = ?", balance, memberId);
    }

    // ---------- DB 상태 조회 ----------

    protected int balanceOf(Long memberId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM member WHERE id = ?", Integer.class, memberId);
    }

    protected String statusOf(Long reservationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId);
    }

    protected int countSlots(Long reservationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation_slot WHERE reservation_id = ?", Integer.class, reservationId);
    }

    protected int countSlotsInRange(Long spaceId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation_slot WHERE space_id = ? AND slot_start >= ? AND slot_start < ?",
                Integer.class, spaceId, fromInclusive, toExclusive);
    }

    /** 예약에 묶인 크레딧 원장 중 type인 행의 개수. */
    protected int countLedger(Long reservationId, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credit_transaction WHERE reservation_id = ? AND type = ?",
                Integer.class, reservationId, type);
    }

    /** 예약에 묶인 크레딧 원장 중 type인 행의 amount 합(부호 포함). 없으면 0. */
    protected int sumLedger(Long reservationId, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM credit_transaction WHERE reservation_id = ? AND type = ?",
                Integer.class, reservationId, type);
    }

    /** 예약 상태 이력 중 to_status가 toStatus인 행의 개수. */
    protected int countHistory(Long reservationId, String toStatus) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation_status_history WHERE reservation_id = ? AND to_status = ?",
                Integer.class, reservationId, toStatus);
    }

    protected int countIdempotencyKeys(Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_key WHERE member_id = ?", Integer.class, memberId);
    }

    // ---------- 동시 실행 ----------

    /**
     * tasks를 전부 준비시킨 뒤 동시에 출발시키고, 각 task의 결과를 같은 순서로 돌려준다.
     * 정상 종료면 null, 예외로 끝났으면 그 예외.
     */
    protected List<Throwable> runConcurrently(List<Runnable> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                Callable<Throwable> job = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        task.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };
                futures.add(executor.submit(job));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    protected static int successCount(List<Throwable> results) {
        return (int) results.stream().filter(Objects::isNull).count();
    }

    /** 실패한 결과만 모은다. BusinessException은 ErrorCode로, 그 외 예외는 예외 자체로 담는다(실패 시 원인이 그대로 보이도록). */
    protected static List<Object> failures(List<Throwable> results) {
        return results.stream()
                .filter(Objects::nonNull)
                .map(t -> t instanceof BusinessException ? (Object) ((BusinessException) t).getErrorCode() : (Object) t)
                .collect(Collectors.toList());
    }

    private static String inList(List<Long> ids) {
        if (ids.isEmpty()) {
            return "0";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}