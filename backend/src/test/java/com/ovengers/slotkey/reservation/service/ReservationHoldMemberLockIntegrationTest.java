package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.global.config.ClockConfig;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.idempotency.IdempotencyKeyRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@Import(ReservationHoldMemberLockIntegrationTest.TestClockConfig.class)
class ReservationHoldMemberLockIntegrationTest extends IntegrationTestSupport {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        public static final LocalDateTime FIXED_DATE_TIME = LocalDateTime.of(2026, 9, 20, 10, 0, 0);
        public static final Instant FIXED_INSTANT = FIXED_DATE_TIME.atZone(ClockConfig.DEFAULT_ZONE).toInstant();

        @Bean
        @Primary
        public Clock testClock() {
            return Clock.fixed(FIXED_INSTANT, ClockConfig.DEFAULT_ZONE);
        }
    }

    @Autowired
    private ReservationHoldService reservationHoldService;

    @SpyBean
    private PricingService pricingService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private Clock clock;

    private Long memberId;
    private Long spaceId;

    @BeforeEach
    void setUp() {
        cleanup();

        Member member = memberRepository.save(new Member(
                "holdlock-" + System.nanoTime() + "@slotkey.test",
                "{noop}password",
                "테스트유저"));
        memberId = member.getId();

        Space space = spaceRepository.save(Space.builder()
                .name("동시성 테스트 회의실")
                .location("강남대로 100")
                .capacity(4)
                .pricePerSlot(3000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());
        spaceId = space.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        auditLogRepository.deleteAllInBatch();
        idempotencyKeyRepository.deleteAllInBatch();
        creditTransactionRepository.deleteAllInBatch();
        reservationStatusHistoryRepository.deleteAllInBatch();
        reservationSlotRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        spaceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동일 회원의 비중첩 HOLD 두 요청을 회원 잠금 직전에 동기화하여 동시 실행해도 데드락 없이 모두 성공한다")
    void sameMemberDisjointHolds_synchronizedBeforeMemberLock_bothSucceedWithoutDeadlock() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 9, 20, 15, 0);
        LocalDateTime start2 = LocalDateTime.of(2026, 9, 20, 15, 0);
        LocalDateTime end2 = LocalDateTime.of(2026, 9, 20, 16, 0);

        CountDownLatch bothReadyLatch = new CountDownLatch(2);

        doAnswer(invocation -> {
            bothReadyLatch.countDown();
            boolean ready = bothReadyLatch.await(5, TimeUnit.SECONDS);
            if (!ready) {
                throw new IllegalStateException("bothReadyLatch timed out");
            }
            return invocation.callRealMethod();
        }).when(pricingService).calculateTotalAmount(anyInt(), anyInt());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationResponse> f1 = executor.submit(() ->
                    reservationHoldService.createHold(memberId, spaceId, start1, end1));
            Future<ReservationResponse> f2 = executor.submit(() ->
                    reservationHoldService.createHold(memberId, spaceId, start2, end2));

            ReservationResponse r1 = f1.get(10, TimeUnit.SECONDS);
            ReservationResponse r2 = f2.get(10, TimeUnit.SECONDS);

            assertThat(r1).isNotNull();
            assertThat(r2).isNotNull();
            assertThat(r1.reservationId()).isNotEqualTo(r2.reservationId());

            List<Reservation> reservations = reservationRepository.findAll();
            assertThat(reservations).hasSize(2);
            assertThat(reservations).allMatch(r -> r.getStatus() == ReservationStatus.HELD);
            assertThat(reservations).allMatch(r -> r.getMemberId().equals(memberId));

            long slotCount = reservationSlotRepository.count();
            assertThat(slotCount).isEqualTo(4L);

            long historyCount = reservationStatusHistoryRepository.count();
            assertThat(historyCount).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("동일 회원이 동일 슬롯에 대해 동시 HOLD를 요청하면 1건만 성공하고 1건은 RESERVATION_SLOT_CONFLICT로 실패한다")
    void sameMemberSameSlotConcurrentHolds_oneSucceedsOneFailsWithSlotConflict() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 20, 15, 0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<ReservationResponse> task = () ->
                    reservationHoldService.createHold(memberId, spaceId, start, end);

            List<Future<ReservationResponse>> futures = executor.invokeAll(List.of(task, task), 10, TimeUnit.SECONDS);
            for (Future<ReservationResponse> future : futures) {
                assertThat(future.isDone()).as("각 태스크는 10초 이내에 완료되어야 한다").isTrue();
            }

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger conflictCount = new AtomicInteger();
            List<Throwable> exceptions = new ArrayList<>();

            for (Future<ReservationResponse> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    successCount.incrementAndGet();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof BusinessException be
                            && be.getErrorCode() == ErrorCode.RESERVATION_SLOT_CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        exceptions.add(cause);
                    }
                }
            }

            assertThat(exceptions).isEmpty();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);

            List<Reservation> reservations = reservationRepository.findAll();
            assertThat(reservations).hasSize(1);
            assertThat(reservations.get(0).getStatus()).isEqualTo(ReservationStatus.HELD);

            long slotCount = reservationSlotRepository.count();
            assertThat(slotCount).isEqualTo(2L);

            long historyCount = reservationStatusHistoryRepository.count();
            assertThat(historyCount).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("탈퇴한 회원이면 ACCOUNT_WITHDRAWN 예외가 발생하고 예약·슬롯·이력 DB 데이터가 남지 않는다")
    void withdrawnMemberHold_throwsAccountWithdrawnAndLeavesNoData() {
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.withdraw(now);
        memberRepository.saveAndFlush(member);

        LocalDateTime start = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 20, 15, 0);

        assertThatThrownBy(() -> reservationHoldService.createHold(memberId, spaceId, start, end))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_WITHDRAWN);

        assertThat(reservationRepository.count()).isZero();
        assertThat(reservationSlotRepository.count()).isZero();
        assertThat(reservationStatusHistoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 회원 ID이면 AUTHENTICATION_REQUIRED 예외가 발생하고 예약·슬롯·이력 DB 데이터가 남지 않는다")
    void nonExistentMemberHold_throwsAuthenticationRequiredAndLeavesNoData() {
        Long nonExistentMemberId = 999_999L;
        LocalDateTime start = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 20, 15, 0);

        assertThatThrownBy(() -> reservationHoldService.createHold(nonExistentMemberId, spaceId, start, end))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTHENTICATION_REQUIRED);

        assertThat(reservationRepository.count()).isZero();
        assertThat(reservationSlotRepository.count()).isZero();
        assertThat(reservationStatusHistoryRepository.count()).isZero();
    }
}
