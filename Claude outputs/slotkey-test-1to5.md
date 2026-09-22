# Slot Key — 테스트 1~5번 복붙본 (dev 기준)

> 이 문서의 코드는 **컴파일·실행하지 못했습니다**(작업 환경에서 Maven 저장소·Docker 접근이 막혀 있음). 문법 파싱까지만 확인했고, 클래스·메서드 시그니처는 dev 소스를 읽고 대조했습니다. 붙여넣은 뒤 `./gradlew test`(통합 테스트는 Docker Desktop 실행 필요)로 먼저 확인해 주세요.

## 적용 순서

| 순서 | 이슈 | 커밋 |
| --- | --- | --- |
| 0 | (문서) | `docs(reservation): define force-cancel refund rules and extend the concurrency test list` |
| 1 | [Reservation] 관리자 강제 취소 시 크레딧 환불 누락 수정 및 동시 취소 방어 | ① `test(reservation): add shared fixtures for reservation integration tests` ② `fix(reservation): refund credits and guard with a conditional update on admin force-cancel` |
| 2 | [Reservation] 결제 확인 반복·동시 요청 시 크레딧 1회 차감 검증 테스트 추가 | `test(reservation): verify payment charges credits exactly once under repeated and concurrent requests` |
| 3 | [Reservation] 예약 연장 단위·통합·동시성 테스트 추가 | `test(reservation): cover extend with unit, rollback and concurrency tests` |
| 4 | [Reservation] 중복·경합 취소 시 환불 1회 보장 테스트 추가 | `test(reservation): verify cancellation refunds exactly once under duplicate and racing requests` |
| 5 | [Access] 출입 토큰 동시 재발급 시 활성 토큰 1개 보장 및 409 처리 | `fix(access): return 409 and keep one active token when door token issues collide` |

**의존 관계:** 1번의 공통 테스트 기반(`ReservationIntegrationTestSupport`)을 2~5번이 사용하고, 4번은 1번의 강제 취소 환불 코드가 있어야 통과합니다. 1번을 먼저 머지하세요. 2·3·4·5번은 서로 독립입니다.

---

## 1. 관리자 강제 취소 환불 (코드 수정 + 테스트)

전제(제가 정한 규칙이니 다르면 알려주세요): `CONFIRMED`·`IN_USE`는 위약금 없이 `total_amount` 전액 환불, `HELD`는 환불 없음. 응답 DTO(`AdminReservationResponse`)는 그대로 둡니다.

### 1-1. 공통 테스트 기반 (신규) — 커밋 ①

**`backend/src/test/java/com/ovengers/slotkey/support/ReservationIntegrationTestSupport.java`** — 신규

```java
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
```

### 1-2. `ReservationRepository` — 메서드 추가 — 커밋 ②

`cancelIfConfirmedAndBeforeStart` 아래, `checkOutIfInUse` 위(`/** 체크아웃/자동 퇴실의 문지기` 주석 바로 앞)에 추가합니다.

```java
    /**
     * 관리자 강제 취소의 문지기(core-domain-decisions 6-4). 조회 시점의 상태(expected)가 그대로일 때만 CANCELLED로 전이한다.
     * 영향 행이 0이면 그 사이 다른 요청(본인 취소·체크인·배치 등)이 먼저 상태를 바꾼 것이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :cancelled, r.cancelledAt = :now " +
            "WHERE r.id = :id AND r.status = :expected")
    int forceCancelIfStatusIs(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("expected") ReservationStatus expected,
            @Param("cancelled") ReservationStatus cancelled
    );

```

### 1-3. `AdminReservationService` — 커밋 ②

**(a) import 2줄 추가**

```java
import com.ovengers.slotkey.credit.service.CreditService;   // AuditLogService import 아래
import java.time.Clock;                                       // java.time.LocalDateTime import 위
```

**(b) 필드 2개 추가** — `doorAccessLogService` 필드 바로 아래(순서 그대로. `@RequiredArgsConstructor` 생성자 순서가 바뀝니다)

```java
    private final CreditService creditService;
    private final Clock clock;
```

**(c) `forceCancel` 메서드를 통째로 교체** (Javadoc 포함, `private String findMemberEmail` 바로 위까지)

```java
    /**
     * 예약 강제 취소 (관리자만, 사유 기록).
     * 상태·시간에 상관없이 CANCELLED로 전이할 수 있다.
     * 이미 COMPLETED/EXPIRED/NO_SHOW/CANCELLED인 경우는 상태 변경 불가(409).
     *
     * 조건부 UPDATE(조회한 상태가 그대로일 때만 전이)가 문지기이며, 영향 행이 1일 때만 같은 트랜잭션에서
     * 후속 처리를 한다(core-domain-decisions 6-4). 그래서 본인 취소·체크인·배치와 겹쳐도 환불은 최대 1회다.
     * 결제한 예약(CONFIRMED/IN_USE)은 위약금 없이 total_amount 전액을 환불하고, 결제 전(HELD)은 환불하지 않는다.
     */
    @Transactional
    public AdminReservationResponse forceCancel(Long reservationId, String reason, Long adminMemberId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 이미 종료된 상태는 취소할 수 없음
        if (reservation.isTerminalState()) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT);
        }

        ReservationStatus previousStatus = reservation.getStatus();
        Long memberId = reservation.getMemberId();
        int totalAmount = reservation.getTotalAmount();
        LocalDateTime now = LocalDateTime.now(clock);

        // 문지기: 조회 시점의 상태가 그대로일 때만 CANCELLED로 전이한다. 0행이면 그 사이 다른 요청이 먼저 상태를 바꾼 것이다.
        int updated = reservationRepository.forceCancelIfStatusIs(
                reservationId, now, previousStatus, ReservationStatus.CANCELLED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "다른 요청이 먼저 예약 상태를 변경했습니다.");
        }

        // 상태 이력 저장
        ReservationStatusHistory history = ReservationStatusHistory.of(
                reservationId,
                adminMemberId,
                previousStatus,
                ReservationStatus.CANCELLED,
                reason,
                now
        );
        statusHistoryRepository.save(history);

        // 슬롯 삭제 (기존 취소와 동일)
        reservationSlotRepository.deleteByReservationId(reservationId);

        // 활성 출입 토큰 revoke. 관리자의 강제 취소이므로 소유자 검사가 없는
        // revokeByReservation(예약 상태 변경에 따른 시스템 경로)을 사용한다.
        doorAccessTokenService.revokeByReservation(reservationId, now, "ADMIN_FORCE_CANCEL");

        // 결제한 예약은 위약금 없이 전액 환불한다. HELD는 결제한 적이 없으므로 환불하지 않는다.
        if (previousStatus != ReservationStatus.HELD) {
            creditService.refund(memberId, reservationId, totalAmount);
        }

        // 감사 로그 기록
        auditLogService.log(
                adminMemberId,
                AuditAction.FORCE_CANCEL_RESERVATION,
                AuditTargetType.RESERVATION,
                reservationId,
                reason,
                previousStatus,
                ReservationStatus.CANCELLED
        );

        // 조건부 UPDATE가 영속성 컨텍스트를 비웠으므로 취소된 최신 상태를 다시 읽어 응답한다.
        Reservation cancelled = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        return AdminReservationResponse.from(
                cancelled,
                findMemberEmail(cancelled.getMemberId()),
                findSpaceName(cancelled.getSpaceId())
        );
    }

```

### 1-4. `Reservation` — 미사용이 된 메서드 삭제 — 커밋 ②

`forceCancel`이 더 이상 쓰지 않으므로 `cancelByAdmin()`(Javadoc 포함)을 지웁니다. `isTerminalState()`는 그대로 씁니다.

```java
    /**
     * 관리자에 의한 강제 취소.
     */
    public void cancelByAdmin() {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
```

### 1-5. `AdminReservationServiceTest` — 전체 교체 — 커밋 ②

생성자 인자(`creditService`, `clock`)가 늘어 기존 테스트가 컴파일되지 않으므로 파일을 통째로 바꿉니다. 기존 테스트(상세 조회·not found·종료 상태)는 그대로 유지했고, 강제 취소 성공 테스트를 환불 검증 포함으로 바꾸고 케이스를 추가했습니다.

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/AdminReservationServiceTest.java`** — 전체 교체

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;
import com.ovengers.slotkey.access.service.DoorAccessLogService;
import com.ovengers.slotkey.access.service.DoorAccessTokenService;
import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.credit.service.CreditService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationDetailResponse;
import com.ovengers.slotkey.reservation.dto.response.AdminReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Long RESERVATION_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final Long SPACE_ID = 5L;
    private static final Long ADMIN_MEMBER_ID = 99L;
    private static final String REASON = "고객 요청에 의한 강제 취소";

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSlotRepository reservationSlotRepository;
    @Mock
    private ReservationStatusHistoryRepository statusHistoryRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DoorAccessTokenService doorAccessTokenService;
    @Mock
    private DoorAccessLogService doorAccessLogService;
    @Mock
    private CreditService creditService;

    private AdminReservationService adminReservationService;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 17, 12, 0);
    private LocalDateTime startTime;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        adminReservationService = new AdminReservationService(
                reservationRepository, reservationSlotRepository, statusHistoryRepository,
                memberRepository, spaceRepository, auditLogService,
                doorAccessTokenService, doorAccessLogService, creditService, clock);
        startTime = LocalDateTime.of(2026, 9, 18, 14, 0);
    }

    private Reservation confirmedReservation() {
        return reservationWithStatus(ReservationStatus.CONFIRMED, 10000);
    }

    private Reservation reservationWithStatus(ReservationStatus status, int totalAmount) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .spaceId(SPACE_ID)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(status)
                .pricePerSlotSnapshot(5000)
                .totalAmount(totalAmount)
                .createdAt(startTime.minusDays(1))
                .build();
    }

    /** 응답 조립에 쓰이는 회원 이메일·공간 이름 조회를 준비한다. */
    private void stubMemberAndSpace() {
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(new Member("user@slotkey.test", "{noop}password", "회원1")));
        given(spaceRepository.findById(SPACE_ID)).willReturn(Optional.of(
                Space.builder().name("공간1").build()));
    }

    // ---------- getReservationDetail ----------

    @Test
    @DisplayName("존재하지 않는 예약을 상세 조회하면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void getReservationDetail_reservationNotFound_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReservationService.getReservationDetail(RESERVATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("예약 상세 조회 시 소유자 검사 없이 출입 로그를 조회해 상태 이력과 함께 응답에 담는다")
    void getReservationDetail_success_mapsStatusHistoryAndAccessLogs() {
        Reservation reservation = confirmedReservation();
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        stubMemberAndSpace();

        ReservationStatusHistory history = ReservationStatusHistory.of(
                RESERVATION_ID, MEMBER_ID, null, ReservationStatus.CONFIRMED, null, startTime.minusHours(1));
        given(statusHistoryRepository.findAllByReservationIdOrderByChangedAtAsc(RESERVATION_ID))
                .willReturn(List.of(history));

        DoorAccessLog allowLog = new DoorAccessLog(
                null, null, null, AccessResult.ALLOW, null, startTime.plusMinutes(1));
        DoorAccessLog denyLog = new DoorAccessLog(
                null, null, null, AccessResult.DENY, AccessDenyReason.TOKEN_REVOKED, startTime.plusMinutes(2));
        given(doorAccessLogService.findAllByReservationId(RESERVATION_ID))
                .willReturn(List.of(allowLog, denyLog));

        AdminReservationDetailResponse response = adminReservationService.getReservationDetail(RESERVATION_ID);

        // 관리자는 예약 소유자가 아니어도 조회할 수 있어야 하므로, owner 검사가 있는
        // findResponsesByReservationId가 아니라 findAllByReservationId가 호출되어야 한다.
        verify(doorAccessLogService).findAllByReservationId(RESERVATION_ID);
        verify(doorAccessLogService, never()).findResponsesByReservationId(any(), any());

        assertThat(response.statusHistory()).hasSize(1);
        assertThat(response.accessLogs()).hasSize(2);
        assertThat(response.accessLogs().get(0).result()).isEqualTo(AccessResult.ALLOW);
        assertThat(response.accessLogs().get(1).result()).isEqualTo(AccessResult.DENY);
        assertThat(response.accessLogs().get(1).reasonCode()).isEqualTo(AccessDenyReason.TOKEN_REVOKED);
    }

    // ---------- forceCancel ----------

    @Test
    @DisplayName("존재하지 않는 예약을 강제 취소하면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void forceCancel_reservationNotFound_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 종료 상태(COMPLETED 등)인 예약은 강제 취소할 수 없고 환불을 포함한 후속 처리도 일어나지 않는다")
    void forceCancel_terminalState_throwsExceptionWithoutSideEffects() {
        Reservation completed = reservationWithStatus(ReservationStatus.COMPLETED, 10000);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(completed));

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verify(reservationRepository, never()).forceCancelIfStatusIs(any(), any(), any(), any());
        verify(reservationSlotRepository, never()).deleteByReservationId(any());
        verify(doorAccessTokenService, never()).revokeByReservation(any(), any(), any());
        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(statusHistoryRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CONFIRMED 예약을 강제 취소하면 CANCELLED로 전이하고 슬롯 반환·토큰 revoke·이력·전액 환불·감사 로그를 모두 수행한다")
    void forceCancel_confirmed_cancelsAndRefundsFullAmount() {
        Reservation confirmed = confirmedReservation();
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(confirmed), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        AdminReservationResponse response =
                adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);
        verify(doorAccessTokenService).revokeByReservation(eq(RESERVATION_ID), eq(now), eq("ADMIN_FORCE_CANCEL"));

        // 관리자 사유의 취소이므로 위약금 없이 결제액 전액을 돌려준다.
        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 10000);
        verify(creditService, never()).penalize(any(), any(), anyInt());

        verify(statusHistoryRepository).save(argThat(history ->
                history.getReservationId().equals(RESERVATION_ID)
                        && history.getChangedByMemberId().equals(ADMIN_MEMBER_ID)
                        && history.getFromStatus() == ReservationStatus.CONFIRMED
                        && history.getToStatus() == ReservationStatus.CANCELLED
                        && history.getReason().equals(REASON)));

        verify(auditLogService).log(
                eq(ADMIN_MEMBER_ID),
                eq(AuditAction.FORCE_CANCEL_RESERVATION),
                eq(AuditTargetType.RESERVATION),
                eq(RESERVATION_ID),
                eq(REASON),
                eq(ReservationStatus.CONFIRMED),
                eq(ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("연장으로 늘어난 총액을 가진 예약을 강제 취소하면 연장분까지 포함한 total_amount 전액을 환불한다")
    void forceCancel_extendedReservation_refundsTotalAmountIncludingExtension() {
        Reservation extended = reservationWithStatus(ReservationStatus.CONFIRMED, 25000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 25000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(extended), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 25000);
    }

    @Test
    @DisplayName("이미 이용 중(IN_USE)인 예약을 강제 취소해도 전액 환불한다")
    void forceCancel_inUse_refundsFullAmount() {
        Reservation inUse = reservationWithStatus(ReservationStatus.IN_USE, 10000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(inUse), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.IN_USE, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(creditService).refund(MEMBER_ID, RESERVATION_ID, 10000);
        verify(statusHistoryRepository).save(argThat(history ->
                history.getFromStatus() == ReservationStatus.IN_USE
                        && history.getToStatus() == ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("결제 전(HELD) 예약을 강제 취소하면 슬롯만 반환하고 환불은 하지 않는다")
    void forceCancel_held_doesNotRefund() {
        Reservation held = reservationWithStatus(ReservationStatus.HELD, 10000);
        Reservation cancelled = reservationWithStatus(ReservationStatus.CANCELLED, 10000);
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(held), Optional.of(cancelled));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.HELD, ReservationStatus.CANCELLED)).willReturn(1);
        stubMemberAndSpace();

        adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID);

        verify(reservationSlotRepository).deleteByReservationId(RESERVATION_ID);
        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(creditService, never()).penalize(any(), any(), anyInt());
    }

    @Test
    @DisplayName("조회 뒤 다른 요청이 먼저 상태를 바꿔 조건부 UPDATE가 0행이면 RESERVATION_STATE_CONFLICT가 발생하고 환불을 포함한 후속 처리가 일어나지 않는다")
    void forceCancel_statusChangedConcurrently_throwsConflictWithoutSideEffects() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(confirmedReservation()));
        given(reservationRepository.forceCancelIfStatusIs(
                RESERVATION_ID, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)).willReturn(0);

        assertThatThrownBy(() -> adminReservationService.forceCancel(RESERVATION_ID, REASON, ADMIN_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verify(creditService, never()).refund(any(), any(), anyInt());
        verify(reservationSlotRepository, never()).deleteByReservationId(any());
        verify(doorAccessTokenService, never()).revokeByReservation(any(), any(), any());
        verify(statusHistoryRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
```

### 1-6. `AdminForceCancelRefundIntegrationTest` (신규) — 커밋 ②

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/AdminForceCancelRefundIntegrationTest.java`** — 신규

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관리자 강제 취소의 환불을 실제 DB로 검증한다(core-domain-decisions 6-4).
 * 단위 테스트(AdminReservationServiceTest)는 환불 "호출"만 확인하므로, 잔액과 원장이 실제로 바뀌는지는 여기서 본다.
 */
class AdminForceCancelRefundIntegrationTest extends ReservationIntegrationTestSupport {

    private static final int INITIAL_BALANCE = 3_000;
    private static final int TOTAL_AMOUNT = 10_000; // 30분 슬롯 2개 × 5,000원
    private static final String REASON = "공간 점검으로 인한 강제 취소";

    @Autowired
    private AdminReservationService adminReservationService;

    private Long memberId;
    private Long adminId;
    private Long spaceId;

    @BeforeEach
    void setUp() {
        memberId = createMember(INITIAL_BALANCE);
        adminId = createMember(0);
        spaceId = createSpace();
    }

    @Test
    @DisplayName("CONFIRMED 예약을 강제 취소하면 결제액 전액이 환불되고 REFUND 원장이 1건 남는다")
    void forceCancel_confirmed_refundsFullAmount() {
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(15, 0));

        adminReservationService.forceCancel(reservationId, REASON, adminId);

        assertThat(statusOf(reservationId)).isEqualTo("CANCELLED");
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE + TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "REFUND")).isEqualTo(1);
        assertThat(sumLedger(reservationId, "REFUND")).isEqualTo(TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "PENALTY")).isZero();
        assertThat(countSlots(reservationId)).isZero();
        assertThat(countHistory(reservationId, "CANCELLED")).isEqualTo(1);
    }

    @Test
    @DisplayName("시작 30분 전이어도 강제 취소는 위약금 없이 전액 환불한다(사용자 취소와 달리 50% 등급이 없다)")
    void forceCancel_imminentStart_stillRefundsFullAmount() {
        LocalDateTime start = now().plusMinutes(30);
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, start, start.plusHours(1));

        adminReservationService.forceCancel(reservationId, REASON, adminId);

        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE + TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "PENALTY")).isZero();
    }

    @Test
    @DisplayName("이용 중(IN_USE)인 예약을 강제 취소해도 전액 환불한다")
    void forceCancel_inUse_refundsFullAmount() {
        LocalDateTime start = now().minusMinutes(30);
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, start, start.plusHours(1));
        // IN_USE는 checked_in_at이 필수(CHECK 제약)이므로 한 번에 함께 채운다.
        jdbcTemplate.update("UPDATE reservation SET status = 'IN_USE', checked_in_at = ? WHERE id = ?",
                start, reservationId);

        adminReservationService.forceCancel(reservationId, REASON, adminId);

        assertThat(statusOf(reservationId)).isEqualTo("CANCELLED");
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE + TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "REFUND")).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 전(HELD) 예약을 강제 취소하면 슬롯만 반환하고 잔액·원장은 그대로다")
    void forceCancel_held_doesNotTouchCredits() {
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.HELD, tomorrowAt(14, 0), tomorrowAt(15, 0));

        adminReservationService.forceCancel(reservationId, REASON, adminId);

        assertThat(statusOf(reservationId)).isEqualTo("CANCELLED");
        assertThat(countSlots(reservationId)).isZero();
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE);
        assertThat(countLedger(reservationId, "REFUND")).isZero();
    }

    @Test
    @DisplayName("이미 강제 취소된 예약을 다시 강제 취소하면 409이고 환불은 1회만 일어난다")
    void forceCancel_twice_refundsOnlyOnce() {
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(15, 0));
        adminReservationService.forceCancel(reservationId, REASON, adminId);

        assertThatThrownBy(() -> adminReservationService.forceCancel(reservationId, REASON, adminId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE + TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "REFUND")).isEqualTo(1);
    }
}
```

---

## 2. 결제 확인 반복·동시 요청 (테스트만)

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationPaymentConcurrencyTest.java`** — 신규

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 확인(POST /reservations/{id}/pay)이 반복·동시 요청에서도 크레딧을 정확히 1회만 차감하는지 실제 DB로 검증한다
 * (core-domain-decisions 2-1, 6-2, 12). 차감 -> 조건부 UPDATE 순서와 "0행이면 전체 롤백" 보장이 대상이다.
 */
class ReservationPaymentConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int INITIAL_BALANCE = 100_000;
    private static final int THREADS = 5;

    @Autowired
    private ReservationHoldService reservationHoldService;
    @Autowired
    private ReservationPaymentConfirmService reservationPaymentConfirmService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long memberId;
    private ReservationResponse hold;

    @BeforeEach
    void setUp() {
        memberId = createMember(INITIAL_BALANCE);
        Long spaceId = createSpace();
        hold = reservationHoldService.createHold(memberId, spaceId, tomorrowAt(14, 0), tomorrowAt(15, 0));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 결제를 3번 순차 요청해도 크레딧은 1회만 차감되고 매번 같은 결과를 돌려받는다")
    void pay_sameKeyRepeated_chargesOnce() {
        ReservationResponse first = pay("key-1");
        ReservationResponse second = pay("key-1");
        ReservationResponse third = pay("key-1");

        assertThat(List.of(first, second, third))
                .extracting(ReservationResponse::status)
                .containsOnly("CONFIRMED");
        assertThat(List.of(second, third))
                .extracting(ReservationResponse::reservationId)
                .containsOnly(first.reservationId());
        assertChargedExactlyOnce();
        assertThat(countIdempotencyKeys(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 예약에 서로 다른 Idempotency-Key로 결제가 동시에 들어와도 정확히 1건만 성공하고 크레딧은 1회만 차감된다")
    void pay_differentKeysConcurrently_chargesOnce() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String key = "key-" + i;
            tasks.add(() -> pay(key));
        }

        List<Throwable> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failures(results))
                .hasSize(THREADS - 1)
                .containsOnly(ErrorCode.RESERVATION_STATE_CONFLICT);
        assertChargedExactlyOnce();
        assertThat(countHistory(hold.reservationId(), "CONFIRMED")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 결제가 동시에 들어와도 크레딧은 1회만 차감되고, 이후 같은 키 재요청은 최초 응답을 그대로 돌려준다")
    void pay_sameKeyConcurrently_chargesOnceAndReplays() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> pay("shared-key"));
        }

        List<Throwable> results = runConcurrently(tasks);

        // 먼저 커밋된 요청이 있으면 늦게 온 요청은 저장된 응답을 재생받아 성공할 수 있다.
        // 밀린 요청은 409로 끝나며, 어느 경우에도 차감은 1회다.
        assertThat(successCount(results)).isGreaterThanOrEqualTo(1);
        assertThat(failures(results)).isSubsetOf(ErrorCode.RESERVATION_STATE_CONFLICT);
        assertChargedExactlyOnce();

        ReservationResponse replayed = pay("shared-key");

        assertThat(replayed.status()).isEqualTo("CONFIRMED");
        assertChargedExactlyOnce();
        assertThat(countIdempotencyKeys(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("잔액 부족으로 결제가 실패하면 Idempotency-Key가 소진되지 않아, 충전 후 같은 키로 재시도하면 성공한다")
    void pay_insufficientBalance_doesNotConsumeIdempotencyKey() {
        setBalance(memberId, 0);

        assertThatThrownBy(() -> pay("retry-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        assertThat(statusOf(hold.reservationId())).isEqualTo("HELD");
        assertThat(countIdempotencyKeys(memberId)).isZero();
        assertThat(countLedger(hold.reservationId(), "RESERVATION_CHARGE")).isZero();

        setBalance(memberId, INITIAL_BALANCE);
        ReservationResponse retried = pay("retry-key");

        assertThat(retried.status()).isEqualTo("CONFIRMED");
        assertChargedExactlyOnce();
        assertThat(countIdempotencyKeys(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 확정 조건부 UPDATE는 hold_expires_at 정각에는 확정하지 않고 1초 전까지만 확정한다(만료 쿼리의 <= 와 경계가 맞물린다)")
    void confirmIfHeldAndNotExpired_atExactExpiry_doesNotConfirm() {
        // DB에는 초 단위로 저장되므로, 응답 객체가 아니라 DB에 실제 저장된 값을 기준으로 삼는다.
        LocalDateTime expiresAt = reservationRepository.findById(hold.reservationId())
                .orElseThrow()
                .getHoldExpiresAt();

        Integer atExpiry = transactionTemplate.execute(status -> reservationRepository.confirmIfHeldAndNotExpired(
                hold.reservationId(), expiresAt, ReservationStatus.HELD, ReservationStatus.CONFIRMED));
        assertThat(atExpiry).isZero();
        assertThat(statusOf(hold.reservationId())).isEqualTo("HELD");

        Integer oneSecondBefore = transactionTemplate.execute(status -> reservationRepository.confirmIfHeldAndNotExpired(
                hold.reservationId(), expiresAt.minusSeconds(1), ReservationStatus.HELD, ReservationStatus.CONFIRMED));
        assertThat(oneSecondBefore).isEqualTo(1);
        assertThat(statusOf(hold.reservationId())).isEqualTo("CONFIRMED");
    }

    private ReservationResponse pay(String idempotencyKey) {
        return reservationPaymentConfirmService.confirm(
                memberId, hold.reservationId(), hold.spaceVersion(), idempotencyKey);
    }

    /** 예약이 CONFIRMED이고, 잔액이 정확히 총액만큼 줄었으며, 결제 원장이 1건뿐이다. */
    private void assertChargedExactlyOnce() {
        assertThat(statusOf(hold.reservationId())).isEqualTo("CONFIRMED");
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE - hold.totalAmount());
        assertThat(countLedger(hold.reservationId(), "RESERVATION_CHARGE")).isEqualTo(1);
        assertThat(sumLedger(hold.reservationId(), "RESERVATION_CHARGE")).isEqualTo(-hold.totalAmount());
    }
}
```

---

## 3. 예약 연장 (테스트만)

연장 코드는 이미 크레딧을 차감합니다(`creditService.charge`). 그래서 코드 수정 없이 단위 테스트 + 통합 테스트만 추가합니다.

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationExtendServiceTest.java`** — 신규 (Mockito 단위)

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.credit.service.CreditService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationExtendServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long RESERVATION_ID = 10L;
    private static final Long SPACE_ID = 5L;
    /** 예약 당시 단가. 공간의 현재 단가와 무관하게 이 값으로 연장 금액을 계산해야 한다. */
    private static final int PRICE_SNAPSHOT = 4000;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSlotService reservationSlotService;
    @Mock
    private CreditService creditService;

    private final PricingService pricingService = new PricingService();

    // 14:00~15:00 예약, 지금은 14:30(이용 중), 15:00~16:00로 연장을 요청한다.
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 18, 14, 30);
    private final LocalDateTime endTime = LocalDateTime.of(2026, 9, 18, 15, 0);
    private final LocalDateTime newEndTime = LocalDateTime.of(2026, 9, 18, 16, 0);
    private final List<LocalDateTime> additionalSlots = List.of(
            LocalDateTime.of(2026, 9, 18, 15, 0), LocalDateTime.of(2026, 9, 18, 15, 30));

    private ReservationExtendService extendService;

    @BeforeEach
    void setUp() {
        extendService = serviceAt(now);
    }

    private ReservationExtendService serviceAt(LocalDateTime at) {
        Clock clock = Clock.fixed(at.atZone(ZONE).toInstant(), ZONE);
        return new ReservationExtendService(
                reservationRepository, reservationSlotService, pricingService, creditService, clock);
    }

    private Reservation reservation(ReservationStatus status, LocalDateTime end, int totalAmount) {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .memberId(MEMBER_ID)
                .spaceId(SPACE_ID)
                .startTime(LocalDateTime.of(2026, 9, 18, 14, 0))
                .endTime(end)
                .status(status)
                .pricePerSlotSnapshot(PRICE_SNAPSHOT)
                .totalAmount(totalAmount)
                .createdAt(LocalDateTime.of(2026, 9, 17, 12, 0))
                .build();
    }

    private Reservation extendable() {
        return reservation(ReservationStatus.CONFIRMED, endTime, 8000);
    }

    @Test
    @DisplayName("존재하지 않는 예약이면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void extend_reservationNotFound_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인이 아닌 예약을 연장하면 FORBIDDEN_NOT_OWNER 예외가 발생하고 슬롯 확보·크레딧 차감을 시도하지 않는다")
    void extend_notOwner_throwsExceptionWithoutSideEffects() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));

        assertThatThrownBy(() -> extendService.extend(OTHER_MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_NOT_OWNER);

        verifyNoInteractions(reservationSlotService, creditService);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "IN_USE"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("연장할 수 없는 상태(HELD·EXPIRED·COMPLETED·CANCELLED·NO_SHOW)이면 RESERVATION_EXTEND_NOT_ALLOWED 예외가 발생한다")
    void extend_notExtendableStatus_throwsException(ReservationStatus status) {
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(reservation(status, endTime, 8000)));

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_EXTEND_NOT_ALLOWED);

        verifyNoInteractions(reservationSlotService, creditService);
    }

    @Test
    @DisplayName("종료 시각 정각에는 연장할 수 없다(now < end_time 일 때만 가능)")
    void extend_atExactEndTime_throwsException() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));

        assertThatThrownBy(() -> serviceAt(endTime).extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_EXTEND_NOT_ALLOWED);

        verifyNoInteractions(reservationSlotService, creditService);
    }

    @Test
    @DisplayName("클라이언트가 아는 종료 시각(expectedEndTime)이 현재 값과 다르면 RESERVATION_STATE_CONFLICT 예외가 발생한다")
    void extend_expectedEndTimeMismatch_throwsConflict() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));

        assertThatThrownBy(() -> extendService.extend(
                MEMBER_ID, RESERVATION_ID, endTime.plusMinutes(30), newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);

        verifyNoInteractions(reservationSlotService, creditService);
    }

    @Test
    @DisplayName("새 종료 시각이 기존 종료 시각과 같거나 이르면 VALIDATION_FAILED 예외가 발생한다")
    void extend_newEndTimeNotAfterCurrent_throwsValidationFailed() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, endTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, endTime.minusMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(reservationSlotService, creditService);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "IN_USE"})
    @DisplayName("연장에 성공하면 슬롯 확보 -> 원 예약 단가로 추가 슬롯 수만큼 차감 -> 종료 시각·총액 UPDATE 순서로 처리한다")
    void extend_success_chargesSnapshotPriceForAdditionalSlots(ReservationStatus status) {
        Reservation before = reservation(status, endTime, 8000);
        Reservation after = reservation(status, newEndTime, 16000);
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(before), Optional.of(after));
        given(reservationSlotService.buildSlotStarts(endTime, newEndTime)).willReturn(additionalSlots);
        // 추가 금액 = 스냅샷 단가 4,000 × 추가 슬롯 2개 = 8,000 -> 새 총액 16,000
        given(reservationRepository.extendIfEndTimeMatches(
                RESERVATION_ID, endTime, newEndTime, 16000,
                ReservationStatus.CONFIRMED, ReservationStatus.IN_USE)).willReturn(1);

        ReservationResponse response = extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime);

        assertThat(response.endTime()).isEqualTo(newEndTime);
        assertThat(response.totalAmount()).isEqualTo(16000);

        InOrder inOrder = inOrder(reservationSlotService, creditService, reservationRepository);
        inOrder.verify(reservationSlotService).secureSlots(RESERVATION_ID, SPACE_ID, additionalSlots);
        inOrder.verify(creditService).charge(MEMBER_ID, RESERVATION_ID, 8000);
        inOrder.verify(reservationRepository).extendIfEndTimeMatches(
                RESERVATION_ID, endTime, newEndTime, 16000,
                ReservationStatus.CONFIRMED, ReservationStatus.IN_USE);
    }

    @Test
    @DisplayName("추가 슬롯이 이미 점유돼 있으면 RESERVATION_SLOT_CONFLICT가 그대로 전파되고 크레딧 차감·종료 시각 UPDATE는 시도하지 않는다")
    void extend_slotConflict_propagatesWithoutCharging() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));
        given(reservationSlotService.buildSlotStarts(endTime, newEndTime)).willReturn(additionalSlots);
        willThrow(new BusinessException(ErrorCode.RESERVATION_SLOT_CONFLICT))
                .given(reservationSlotService).secureSlots(RESERVATION_ID, SPACE_ID, additionalSlots);

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_SLOT_CONFLICT);

        verifyNoInteractions(creditService);
        verify(reservationRepository, never()).extendIfEndTimeMatches(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("잔액이 부족하면 INSUFFICIENT_BALANCE가 전파되고 종료 시각 UPDATE는 시도하지 않는다")
    void extend_insufficientBalance_propagatesWithoutUpdatingEndTime() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));
        given(reservationSlotService.buildSlotStarts(endTime, newEndTime)).willReturn(additionalSlots);
        given(creditService.charge(MEMBER_ID, RESERVATION_ID, 8000))
                .willThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE));

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        verify(reservationRepository, never()).extendIfEndTimeMatches(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("조건부 UPDATE가 0행이면(중복 연장·취소와의 경합) RESERVATION_STATE_CONFLICT 예외가 발생한다")
    void extend_conditionalUpdateAffectsNoRow_throwsConflict() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(extendable()));
        given(reservationSlotService.buildSlotStarts(endTime, newEndTime)).willReturn(additionalSlots);
        given(reservationRepository.extendIfEndTimeMatches(
                RESERVATION_ID, endTime, newEndTime, 16000,
                ReservationStatus.CONFIRMED, ReservationStatus.IN_USE)).willReturn(0);

        assertThatThrownBy(() -> extendService.extend(MEMBER_ID, RESERVATION_ID, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_STATE_CONFLICT);
    }
}
```

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationExtendIntegrationTest.java`** — 신규 (실제 DB)

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예약 연장(POST /reservations/{id}/extend)을 실제 DB로 검증한다(core-domain-decisions 4-3, 7).
 * 슬롯 삽입·크레딧 차감·종료 시각 UPDATE가 한 트랜잭션이라는 점(실패 시 전부 롤백)과 동시 요청 하의 정합성이 대상이다.
 * 기본 예약: 내일 14:00~15:00(슬롯 2개, 총액 10,000원, CONFIRMED). 연장 목표: 16:00.
 */
class ReservationExtendIntegrationTest extends ReservationIntegrationTestSupport {

    private static final int INITIAL_BALANCE = 100_000;
    private static final int ORIGINAL_TOTAL = 10_000;
    private static final int EXTENSION_COST = 10_000; // 추가 슬롯 2개 × 5,000원
    private static final int THREADS = 5;

    @Autowired
    private ReservationExtendService reservationExtendService;
    @Autowired
    private ReservationHoldService reservationHoldService;
    @Autowired
    private ReservationCancelService reservationCancelService;

    private Long memberId;
    private Long spaceId;
    private Long reservationId;
    private LocalDateTime endTime;
    private LocalDateTime newEndTime;

    @BeforeEach
    void setUp() {
        memberId = createMember(INITIAL_BALANCE);
        spaceId = createSpace();
        endTime = tomorrowAt(15, 0);
        newEndTime = tomorrowAt(16, 0);
        reservationId = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), endTime);
    }

    @Test
    @DisplayName("연장에 성공하면 추가 슬롯이 생기고, 추가 금액만큼 차감되며, 종료 시각과 총액이 갱신된다")
    void extend_success_updatesSlotsBalanceAndReservation() {
        ReservationResponse response = reservationExtendService.extend(memberId, reservationId, endTime, newEndTime);

        assertThat(response.endTime()).isEqualTo(newEndTime);
        assertThat(response.totalAmount()).isEqualTo(ORIGINAL_TOTAL + EXTENSION_COST);
        assertThat(countSlots(reservationId)).isEqualTo(4);
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE - EXTENSION_COST);
        assertThat(countLedger(reservationId, "RESERVATION_CHARGE")).isEqualTo(1);
        assertThat(sumLedger(reservationId, "RESERVATION_CHARGE")).isEqualTo(-EXTENSION_COST);
    }

    @Test
    @DisplayName("예약 뒤에 공간 가격이 올라도 연장 금액은 원 예약의 단가(price_per_slot_snapshot)로 계산한다")
    void extend_afterSpacePriceIncrease_usesOriginalSnapshotPrice() {
        jdbcTemplate.update("UPDATE spaces SET price_per_slot = 9000, version = version + 1 WHERE id = ?", spaceId);

        reservationExtendService.extend(memberId, reservationId, endTime, newEndTime);

        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE - EXTENSION_COST);
    }

    @Test
    @DisplayName("추가 슬롯 중 일부가 이미 점유돼 있으면 409이고, 먼저 넣은 슬롯·크레딧 차감이 모두 롤백되어 원 예약이 그대로다")
    void extend_partialSlotConflict_rollsBackEverything() {
        // 다른 회원이 15:30~16:00을 이미 확정해 둔 상태: 연장은 15:00 삽입에는 성공하고 15:30에서 충돌한다.
        Long otherMemberId = createMember(INITIAL_BALANCE);
        createReservation(otherMemberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(15, 30), newEndTime);

        assertThatThrownBy(() -> reservationExtendService.extend(memberId, reservationId, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_SLOT_CONFLICT);

        assertThat(countSlots(reservationId)).isEqualTo(2);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getEndTime()).isEqualTo(endTime);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getTotalAmount())
                .isEqualTo(ORIGINAL_TOTAL);
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE);
        assertThat(countLedger(reservationId, "RESERVATION_CHARGE")).isZero();
    }

    @Test
    @DisplayName("잔액이 부족하면 422이고, 이미 넣은 추가 슬롯이 롤백되어 슬롯·종료 시각·잔액이 그대로다")
    void extend_insufficientBalance_rollsBackSlots() {
        setBalance(memberId, EXTENSION_COST - 1_000);

        assertThatThrownBy(() -> reservationExtendService.extend(memberId, reservationId, endTime, newEndTime))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        assertThat(countSlots(reservationId)).isEqualTo(2);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getEndTime()).isEqualTo(endTime);
        assertThat(balanceOf(memberId)).isEqualTo(EXTENSION_COST - 1_000);
        assertThat(countLedger(reservationId, "RESERVATION_CHARGE")).isZero();
    }

    @Test
    @DisplayName("같은 예약에 같은 연장 요청이 동시에 들어와도 정확히 1건만 성공하고 슬롯·크레딧은 1회분만 반영된다")
    void extend_sameRequestConcurrently_onlyOneSucceeds() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> reservationExtendService.extend(memberId, reservationId, endTime, newEndTime));
        }

        List<Throwable> results = runConcurrently(tasks);

        // 진 요청은 슬롯 UNIQUE 충돌(RESERVATION_SLOT_CONFLICT) 또는, 이미 늘어난 종료 시각을 본 뒤의
        // 낙관적 검사 실패(RESERVATION_STATE_CONFLICT)로 끝난다.
        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failures(results))
                .hasSize(THREADS - 1)
                .isSubsetOf(ErrorCode.RESERVATION_SLOT_CONFLICT, ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(countSlots(reservationId)).isEqualTo(4);
        assertThat(balanceOf(memberId)).isEqualTo(INITIAL_BALANCE - EXTENSION_COST);
        assertThat(countLedger(reservationId, "RESERVATION_CHARGE")).isEqualTo(1);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getEndTime()).isEqualTo(newEndTime);
    }

    @Test
    @DisplayName("연장과 같은 시간대의 신규 예약(HOLD)이 동시에 들어오면 하나만 성공하고 그 시간대 슬롯은 한 예약 몫만 남는다")
    void extend_concurrentWithNewHold_onlyOneSucceeds() {
        Long otherMemberId = createMember(INITIAL_BALANCE);
        List<Runnable> tasks = List.of(
                () -> reservationExtendService.extend(memberId, reservationId, endTime, newEndTime),
                () -> reservationHoldService.createHold(otherMemberId, spaceId, endTime, newEndTime));

        List<Throwable> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failures(results)).containsExactly(ErrorCode.RESERVATION_SLOT_CONFLICT);
        assertThat(countSlotsInRange(spaceId, endTime, newEndTime)).isEqualTo(2);
    }

    @Test
    @DisplayName("연장한 예약을 시작 1시간 전 이전에 취소하면 연장분까지 포함한 총액 전액이 환불된다")
    void extendThenCancel_refundsTotalAmountIncludingExtension() {
        reservationExtendService.extend(memberId, reservationId, endTime, newEndTime);

        reservationCancelService.cancel(memberId, reservationId);

        assertThat(statusOf(reservationId)).isEqualTo("CANCELLED");
        assertThat(sumLedger(reservationId, "REFUND")).isEqualTo(ORIGINAL_TOTAL + EXTENSION_COST);
        assertThat(countLedger(reservationId, "PENALTY")).isZero();
        assertThat(countSlots(reservationId)).isZero();
        // 원 예약 결제분은 이 테스트가 만든 잔액에 반영돼 있지 않으므로: 초기 - 연장 차감 + 전액 환불
        assertThat(balanceOf(memberId))
                .isEqualTo(INITIAL_BALANCE - EXTENSION_COST + ORIGINAL_TOTAL + EXTENSION_COST);
    }
}
```

---

## 4. 중복·경합 취소 (테스트만)

같은 사용자의 중복 요청(더블 클릭 등)과, 본인 취소 ↔ 관리자 강제 취소 경합을 검증합니다. **1번 머지 후** 실행하세요.

**`backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationCancelConcurrencyTest.java`** — 신규

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 예약에 취소가 겹쳐도 환불이 정확히 1회만 일어나는지 실제 DB로 검증한다(core-domain-decisions 6-2).
 * 예약자 본인만 취소할 수 있으므로 "서로 다른 사용자끼리의 경합"이 아니라 다음 두 경우다.
 * 1) 같은 사용자의 중복 요청(더블 클릭, 탭 두 개, 네트워크 재시도)
 * 2) 사용자 본인 취소 ↔ 관리자 강제 취소가 같은 순간에 처리되는 경우
 */
class ReservationCancelConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int INITIAL_BALANCE = 3_000;
    private static final int TOTAL_AMOUNT = 10_000; // 30분 슬롯 2개 × 5,000원
    private static final int THREADS = 5;
    private static final int ROUNDS = 5;

    @Autowired
    private ReservationCancelService reservationCancelService;
    @Autowired
    private AdminReservationService adminReservationService;

    private Long memberId;
    private Long adminId;
    private Long spaceId;

    @BeforeEach
    void setUp() {
        memberId = createMember(INITIAL_BALANCE);
        adminId = createMember(0);
        spaceId = createSpace();
    }

    @Test
    @DisplayName("같은 사용자가 같은 예약을 동시에 여러 번 취소해도 정확히 1건만 성공하고 환불은 1회만 일어난다")
    void cancel_sameUserConcurrently_refundsOnce() {
        Long reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(15, 0));
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> reservationCancelService.cancel(memberId, reservationId));
        }

        List<Throwable> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failures(results))
                .hasSize(THREADS - 1)
                .containsOnly(ErrorCode.RESERVATION_STATE_CONFLICT);
        assertRefundedExactlyOnce(reservationId, INITIAL_BALANCE);
    }

    @Test
    @DisplayName("사용자 본인 취소와 관리자 강제 취소가 동시에 처리돼도 정확히 한쪽만 성공하고 환불은 1회만 일어난다")
    void cancel_userAndAdminConcurrently_refundsOnce() {
        // 두 요청이 실제로 겹칠 확률을 높이기 위해 서로 다른 예약으로 여러 판 반복한다.
        for (int round = 0; round < ROUNDS; round++) {
            int balanceBefore = balanceOf(memberId);
            Long reservationId = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED,
                    tomorrowAt(9 + round * 2, 0), tomorrowAt(10 + round * 2, 0));
            List<Runnable> tasks = List.of(
                    () -> reservationCancelService.cancel(memberId, reservationId),
                    () -> adminReservationService.forceCancel(reservationId, "동시 취소 경합 검증", adminId));

            List<Throwable> results = runConcurrently(tasks);

            assertThat(successCount(results)).as("round %d", round).isEqualTo(1);
            assertThat(failures(results)).as("round %d", round)
                    .containsExactly(ErrorCode.RESERVATION_STATE_CONFLICT);
            assertRefundedExactlyOnce(reservationId, balanceBefore);
        }
    }

    /** CANCELLED이고, 잔액이 정확히 총액만큼 늘었으며, 환불 원장 1건·위약금 없음·슬롯 전부 반환·취소 이력 1건이다. */
    private void assertRefundedExactlyOnce(Long reservationId, int balanceBefore) {
        assertThat(statusOf(reservationId)).isEqualTo("CANCELLED");
        assertThat(balanceOf(memberId)).isEqualTo(balanceBefore + TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "REFUND")).isEqualTo(1);
        assertThat(sumLedger(reservationId, "REFUND")).isEqualTo(TOTAL_AMOUNT);
        assertThat(countLedger(reservationId, "PENALTY")).isZero();
        assertThat(countSlots(reservationId)).isZero();
        assertThat(countHistory(reservationId, "CANCELLED")).isEqualTo(1);
    }
}
```

---

## 5. 출입 토큰 동시 재발급 (작은 코드 수정 + 테스트)

현재 `issue()`는 UNIQUE 위반(`DataIntegrityViolationException`)을 변환하지 않아, 동시 발급에서 진 요청이 500으로 나갈 수 있습니다. 활성 토큰 1개는 DB 제약이 이미 보장하므로, 응답만 409로 바꿉니다.

### 5-1. `DoorAccessTokenService.issue()` 수정

**(a) import 1줄 추가** — `lombok.RequiredArgsConstructor` import 아래

```java
import org.springframework.dao.DataIntegrityViolationException;
```

**(b) `// DB에는 해시된 토큰 저장` 아래 `create(...)` 호출을 교체**

```java
        // DB에는 해시된 토큰 저장. 같은 예약에 발급 요청이 동시에 들어오면 활성 토큰 UNIQUE 제약
        // (active_reservation_id)이 하나만 통과시키므로, 진 요청은 500이 아니라 409로 돌려준다.
        try {
            create(
                    reservation,
                    tokenHash,
                    issuedAt
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.RESERVATION_STATE_CONFLICT,
                    "동시에 다른 발급 요청이 처리되었습니다. 다시 시도해주세요."
            );
        }
```

### 5-2. `DoorAccessTokenConcurrencyTest` (신규)

**`backend/src/test/java/com/ovengers/slotkey/access/service/DoorAccessTokenConcurrencyTest.java`** — 신규

```java
package com.ovengers.slotkey.access.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "예약당 활성 출입 토큰은 1개"(CLAUDE.md 규칙 5)를 실제 DB 제약(door_access_token.active_reservation_id UNIQUE)으로 검증한다.
 * 같은 예약에 발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개여야 하고,
 * 제약에 걸려 진 요청은 500이 아니라 409(RESERVATION_STATE_CONFLICT)로 끝나야 한다.
 */
class DoorAccessTokenConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int THREADS = 8;

    @Autowired
    private DoorAccessTokenService doorAccessTokenService;

    private Long memberId;
    private Long reservationId;

    @BeforeEach
    void setUp() {
        memberId = createMember(0);
        Long spaceId = createSpace();
        reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(15, 0));
    }

    @Test
    @DisplayName("토큰이 없는 예약에 발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개이고, 진 요청은 409로 끝난다")
    void issue_firstIssueConcurrently_leavesExactlyOneActiveToken() {
        List<Throwable> results = runConcurrently(issueTasks());

        assertThat(successCount(results)).isGreaterThanOrEqualTo(1);
        assertThat(failures(results)).isSubsetOf(ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(activeTokenCount()).isEqualTo(1);
        // 성공한 발급 하나당 토큰이 정확히 하나 생긴다(실패한 요청은 롤백되어 흔적이 없다).
        assertThat(totalTokenCount()).isEqualTo(successCount(results));
    }

    @Test
    @DisplayName("이미 활성 토큰이 있는 예약에 재발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개이고, 나머지는 REISSUED로 폐기돼 있다")
    void issue_reissueConcurrently_leavesExactlyOneActiveToken() {
        doorAccessTokenService.issue(memberId, reservationId);

        List<Throwable> results = runConcurrently(issueTasks());

        assertThat(failures(results)).isSubsetOf(ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(activeTokenCount()).isEqualTo(1);
        int total = totalTokenCount();
        assertThat(total).isEqualTo(1 + successCount(results));
        assertThat(revokedTokenCount("REISSUED")).isEqualTo(total - 1);
    }

    private List<Runnable> issueTasks() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> doorAccessTokenService.issue(memberId, reservationId));
        }
        return tasks;
    }

    private int activeTokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ? AND revoked_at IS NULL",
                Integer.class, reservationId);
    }

    private int totalTokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ?", Integer.class, reservationId);
    }

    private int revokedTokenCount(String reason) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ? AND revoked_at IS NOT NULL AND revoke_reason = ?",
                Integer.class, reservationId, reason);
    }
}
```

---

## 문서 변경 (`docs/`)

> 줄 번호는 현재 dev 파일 기준입니다. 위에서부터 넣으면 뒤쪽 줄이 밀리므로 **아래쪽 항목부터** 넣거나, 괄호 안 문구를 기준으로 위치를 찾으세요.

### `docs/core-domain-decisions.md`

**(A) 3-4 전이 주체 표 — 189행(`IN_USE → COMPLETED` 행) 아래에 1행 추가**

```markdown
| `HELD/CONFIRMED/IN_USE → CANCELLED` (강제 취소) | 관리자 (6-4) |
```

**(B) 6-3 노쇼 뒤 — 310행(`- 슬롯을 반환하는 이유…`) 아래, `---`(312행) 위에 새 절 추가**

````markdown

### 6-4. 관리자 강제 취소와 환불

관리자 강제 취소(`POST /admin/reservations/{id}/force-cancel`)는 사용자 취소(6-1)와 달리 **시작 시각과 무관하게** 가능하다. 취소 사유가 사용자가 아니라 운영 쪽 결정이므로 **위약금은 없고**, 결제한 금액은 전액 돌려준다.

| 취소 직전 상태 | 슬롯 | 환불 |
| --- | --- | --- |
| `HELD` | 삭제 | 없음 (결제한 적이 없다) |
| `CONFIRMED` | 삭제 | **100%** (연장분 포함 `total_amount` 전액, `REFUND` 1줄) |
| `IN_USE` | 삭제 | **100%** (이미 이용한 시간도 차감하지 않는다) |
| `COMPLETED` / `EXPIRED` / `NO_SHOW` / `CANCELLED` | — | 취소 불가 (409) |

처리 순서는 6-2와 같은 골격이다.

```
1) 조건부 UPDATE로 상태 전이  ← 문지기
   UPDATE reservation SET status='CANCELLED', cancelled_at=:now
   WHERE id=:id AND status=:조회 시점의 상태
   → 영향 행 0이면 즉시 중단, 409

── 이하 같은 트랜잭션 ──
2) 상태 이력 저장 (사유 포함)
3) 슬롯 행 삭제
4) 활성 토큰 revoke
5) 크레딧 환급 + 원장 기록 (REFUND 전액, HELD는 생략)
6) audit_log 기록
7) COMMIT
```

- 문지기를 `status='CONFIRMED'`로 고정하지 않고 **조회한 상태 그대로**로 두는 이유: 조회와 UPDATE 사이에 다른 요청이 상태를 바꿨다면(본인 취소, 체크인 등) 0행이 되어 **환불이 두 번 나가지 않는다.**
- 본인 취소와 강제 취소가 같은 순간에 와도 문지기에서 한쪽만 통과하므로 환불은 정확히 1회다(12장 동시성 테스트).

````

**(C) 12장 테스트 목록 — 4곳에 추가**

- 505행(`같은 예약에 연장 요청 2회 동시 → 하나만 성공`) 아래:

```markdown
- 같은 예약에 서로 다른 `Idempotency-Key`로 결제 N건 동시 → 정확히 1건 성공, 차감 1회, 나머지는 409
- 같은 `Idempotency-Key`로 결제 N건 동시 → 차감 1회, 이후 같은 키 재요청은 최초 응답 그대로
- 같은 예약에 본인 취소 N건 동시(더블 클릭) → 정확히 1건 성공, 환불 1회
- 본인 취소 ↔ 관리자 강제 취소 동시 → 정확히 1건 성공, 환불 1회
- 같은 예약에 도어 토큰 발급 N건 동시 → 활성 토큰 정확히 1개, 진 요청은 409(500 아님)
```

- 512행(`50% 환불 시 REFUND + PENALTY 두 건 기록`) 아래:

```markdown
- 결제 실패(잔액 부족) 후 충전하고 같은 `Idempotency-Key`로 재시도 → 성공 (실패한 요청은 키를 소진하지 않음)
- 연장 성공 → 원 예약 단가 × 추가 슬롯 수만큼만 차감, `RESERVATION_CHARGE` 1건 / 연장 실패(슬롯 충돌·잔액 부족) → 차감·추가 슬롯 없음(롤백)
- 연장한 예약을 시작 1시간 전까지 취소 → 연장분 포함 전액 환불
- 강제 취소: `CONFIRMED`·`IN_USE`는 `total_amount` 전액 `REFUND`(위약금 없음, 시작 임박 포함), `HELD`는 환불 없음
```

- 519행(`시작 이후 취소 시도 → 거절`) 아래:

```markdown
- 종료 상태 예약 강제 취소, 이미 강제 취소된 예약 재강제 취소 → 409, 환불 없음
```

- 525행(`HOLD: hold_expires_at-1s / hold_expires_at`) 아래:

```markdown
HOLD 결제: `now == hold_expires_at`는 확정하지 않는다(`now < hold_expires_at`만 확정). 만료 쿼리의 `<=`와 경계가 맞물린다
```

### `docs/api-spec.md`

**(A) 6장 157행(`POST /admin/reservations/{reservationId}/force-cancel`) 통째로 교체**

```markdown
- `POST /admin/reservations/{reservationId}/force-cancel` (PLATFORM_ADMIN): 바디 `{ "reason": "..." }` 1~500자 필수, 응답 `AdminReservationResponse`. 취소 가능 상태는 `HELD`/`CONFIRMED`/`IN_USE`(그 외 종료 상태는 409). 조회 시점 상태 기준 조건부 UPDATE(`WHERE id=:id AND status=:조회 시점 상태`) → 같은 트랜잭션에서 상태 이력(사유 포함) + 슬롯 삭제 + 활성 토큰 revoke + **크레딧 환불(`CONFIRMED`/`IN_USE`는 `total_amount` 전액 `REFUND`, 위약금 없음 / `HELD`는 환불 없음)** + audit_log 기록. 오류: RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409, 종료 상태이거나 동시 요청에 밀림). **관리자도 이 API 외의 경로로 타인 예약을 취소하거나 도어 토큰을 발급받을 수 없다** (핵심 차별점)
```

**(B) 7장 163행(`POST /reservations/{reservationId}/door-token`) 끝의 `오류:` 목록 뒤에 덧붙임**

```markdown
, RESERVATION_STATE_CONFLICT(409, 같은 예약의 발급 요청이 동시에 겹쳐 활성 토큰 UNIQUE 제약에 밀린 경우 — 재시도하면 새로 발급된다)
```

