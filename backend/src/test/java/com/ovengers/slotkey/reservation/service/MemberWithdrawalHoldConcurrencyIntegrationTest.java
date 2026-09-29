package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.repository.DoorAccessTokenRepository;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.global.config.ClockConfig;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.idempotency.IdempotencyKeyRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.member.service.MemberWithdrawalService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(MemberWithdrawalHoldConcurrencyIntegrationTest.TestClockConfig.class)
class MemberWithdrawalHoldConcurrencyIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DoorAccessTokenRepository doorAccessTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private Long memberId;
    private Long spaceId;
    private static final String RAW_PASSWORD = "testPassword123!";

    @BeforeEach
    void setUp() {
        cleanup();

        Member member = memberRepository.save(new Member(
                "withdraw-hold-" + System.nanoTime() + "@slotkey.test",
                passwordEncoder.encode(RAW_PASSWORD),
                "탈퇴테스트유저"));
        memberId = member.getId();

        Space space = spaceRepository.save(Space.builder()
                .name("경합 테스트 회의실")
                .location("테헤란로 200")
                .capacity(4)
                .pricePerSlot(2500L)
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
        doorAccessTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
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
    @DisplayName("HOLD 선행 시나리오: HOLD가 먼저 회원 잠금을 확보하면 탈퇴는 대기 후 WITHDRAWAL_ACTIVE_RESERVATION으로 거절된다")
    void holdPreceding_withdrawalBlockedAndFailsWithActiveReservation() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 20, 15, 0);

        CountDownLatch holdCreatedLatch = new CountDownLatch(1);
        CountDownLatch allowHoldCommitLatch = new CountDownLatch(1);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread 1: HOLD 생성 후 트랜잭션 커밋 전 대기 (회원 배타 잠금 유지)
            Future<ReservationResponse> holdFuture = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        ReservationResponse response = reservationHoldService.createHold(memberId, spaceId, start, end);
                        holdCreatedLatch.countDown();
                        try {
                            boolean released = allowHoldCommitLatch.await(10, TimeUnit.SECONDS);
                            if (!released) {
                                throw new IllegalStateException("allowHoldCommitLatch timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return response;
                    }));

            // HOLD가 회원 잠금을 잡을 때까지 대기
            assertThat(holdCreatedLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // 탈퇴 스레드가 실제 findByIdForUpdate에 진입함을 관측하기 위한 프록시 설정
            CountDownLatch withdrawEnteredLatch = new CountDownLatch(1);
            MemberRepository originalRepo = (MemberRepository) ReflectionTestUtils.getField(memberWithdrawalService, "memberRepository");
            MemberRepository proxyRepo = (MemberRepository) Proxy.newProxyInstance(
                    MemberRepository.class.getClassLoader(),
                    new Class<?>[]{MemberRepository.class},
                    (proxy, method, args) -> {
                        if ("findByIdForUpdate".equals(method.getName())
                                && Long.valueOf(memberId).equals(args[0])) {
                            withdrawEnteredLatch.countDown();
                        }
                        try {
                            return method.invoke(originalRepo, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
            );
            ReflectionTestUtils.setField(memberWithdrawalService, "memberRepository", proxyRepo);

            Future<?> withdrawFuture;
            try {
                // Thread 2: 탈퇴 요청 시작 -> Member X 락 대기에 진입
                withdrawFuture = executor.submit(() ->
                        memberWithdrawalService.withdraw(memberId, RAW_PASSWORD));

                // 탈퇴 스레드가 실제 findByIdForUpdate 저장소 잠금 호출에 진입했음을 확인
                assertThat(withdrawEnteredLatch.await(5, TimeUnit.SECONDS)).isTrue();
                // 선행 HOLD 트랜잭션이 커밋되기 전까지 후행 탈퇴 작업이 완료되지 못함을 입증
                assertThat(withdrawFuture.isDone()).isFalse();

                // HOLD 커밋 허용 -> 잠금 해제
                allowHoldCommitLatch.countDown();

                // HOLD 성공 확인
                ReservationResponse holdResponse = holdFuture.get(10, TimeUnit.SECONDS);
                assertThat(holdResponse).isNotNull();
                assertThat(holdResponse.status()).isEqualTo("HELD");

                // 탈퇴 요청은 활성 예약으로 인해 WITHDRAWAL_ACTIVE_RESERVATION 예외 발생 확인
                assertThatThrownBy(() -> {
                    try {
                        withdrawFuture.get(10, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        throw e.getCause();
                    }
                }).isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WITHDRAWAL_ACTIVE_RESERVATION);

                // 회원 상태는 ACTIVE 유지
                Member member = memberRepository.findById(memberId).orElseThrow();
                assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);

                // DB에 예약 1건, 슬롯 2건, 상태 이력 1건 정상 영속화
                List<Reservation> reservations = reservationRepository.findAll();
                assertThat(reservations).hasSize(1);
                assertThat(reservations.get(0).getStatus()).isEqualTo(ReservationStatus.HELD);
                assertThat(reservationSlotRepository.count()).isEqualTo(2L);
                assertThat(reservationStatusHistoryRepository.count()).isEqualTo(1L);
            } finally {
                ReflectionTestUtils.setField(memberWithdrawalService, "memberRepository", originalRepo);
            }
        } finally {
            allowHoldCommitLatch.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("탈퇴 선행 시나리오: 탈퇴가 먼저 회원 잠금을 확보하면 HOLD는 대기 후 ACCOUNT_WITHDRAWN으로 거절된다")
    void withdrawalPreceding_holdBlockedAndFailsWithAccountWithdrawn() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 9, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 20, 15, 0);

        CountDownLatch withdrawExecutedLatch = new CountDownLatch(1);
        CountDownLatch allowWithdrawCommitLatch = new CountDownLatch(1);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread 1: 탈퇴 수행 후 트랜잭션 커밋 전 대기 (회원 배타 잠금 유지)
            Future<Void> withdrawFuture = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        memberWithdrawalService.withdraw(memberId, RAW_PASSWORD);
                        withdrawExecutedLatch.countDown();
                        try {
                            boolean released = allowWithdrawCommitLatch.await(10, TimeUnit.SECONDS);
                            if (!released) {
                                throw new IllegalStateException("allowWithdrawCommitLatch timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return null;
                    }));

            // 탈퇴가 회원 잠금을 잡을 때까지 대기
            assertThat(withdrawExecutedLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // HOLD 스레드가 실제 findByIdForUpdate에 진입함을 관측하기 위한 프록시 설정
            CountDownLatch holdEnteredLatch = new CountDownLatch(1);
            MemberRepository originalRepo = (MemberRepository) ReflectionTestUtils.getField(reservationHoldService, "memberRepository");
            MemberRepository proxyRepo = (MemberRepository) Proxy.newProxyInstance(
                    MemberRepository.class.getClassLoader(),
                    new Class<?>[]{MemberRepository.class},
                    (proxy, method, args) -> {
                        if ("findByIdForUpdate".equals(method.getName())
                                && Long.valueOf(memberId).equals(args[0])) {
                            holdEnteredLatch.countDown();
                        }
                        try {
                            return method.invoke(originalRepo, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
            );
            ReflectionTestUtils.setField(reservationHoldService, "memberRepository", proxyRepo);

            Future<ReservationResponse> holdFuture;
            try {
                // Thread 2: HOLD 요청 시작 -> Member X 락 대기에 진입
                holdFuture = executor.submit(() ->
                        reservationHoldService.createHold(memberId, spaceId, start, end));

                // HOLD 스레드가 실제 findByIdForUpdate 저장소 잠금 호출에 진입했음을 확인
                assertThat(holdEnteredLatch.await(5, TimeUnit.SECONDS)).isTrue();
                // 선행 탈퇴 트랜잭션이 커밋되기 전까지 후행 HOLD 작업이 완료되지 못함을 입증
                assertThat(holdFuture.isDone()).isFalse();

                // 탈퇴 커밋 허용 -> 잠금 해제
                allowWithdrawCommitLatch.countDown();

                // 탈퇴 성공 확인
                withdrawFuture.get(10, TimeUnit.SECONDS);

                // HOLD 요청은 탈퇴로 인해 ACCOUNT_WITHDRAWN 예외 발생 확인
                assertThatThrownBy(() -> {
                    try {
                        holdFuture.get(10, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        throw e.getCause();
                    }
                }).isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_WITHDRAWN);

                // 회원 상태는 WITHDRAWN 유지
                Member member = memberRepository.findById(memberId).orElseThrow();
                assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);

                // 신규 예약·슬롯·이력은 0건
                assertThat(reservationRepository.count()).isZero();
                assertThat(reservationSlotRepository.count()).isZero();
                assertThat(reservationStatusHistoryRepository.count()).isZero();
            } finally {
                ReflectionTestUtils.setField(reservationHoldService, "memberRepository", originalRepo);
            }
        } finally {
            allowWithdrawCommitLatch.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
