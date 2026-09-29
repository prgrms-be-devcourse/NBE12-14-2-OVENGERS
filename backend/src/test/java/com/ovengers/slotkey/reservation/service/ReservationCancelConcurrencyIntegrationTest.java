package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.credit.service.CreditServiceImpl;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * 취소(ReservationCancelService)와 연장(ReservationExtendService), 취소와 신규 HOLD(ReservationHoldService) 간
 * 동시 경합 시 자원 획득 순서(Member-before-Slot) 일치로 인한 데드락(MySQL 1213) 방지 통합 테스트.
 */
class ReservationCancelConcurrencyIntegrationTest extends ReservationIntegrationTestSupport {

    @Autowired
    private ReservationCancelService cancelService;

    @Autowired
    private ReservationExtendService extendService;

    @Autowired
    private ReservationHoldService holdService;

    @Autowired
    private AdminReservationService adminReservationService;

    @SpyBean
    private CreditServiceImpl creditService;

    @Test
    @DisplayName("동일 회원의 예약A 취소(15~16시)와 예약B 연장(14~15시 -> 15~16시) 동시 경합 시 데드락 없이 모두 정상 완료")
    void cancelAndExtendOtherReservation_mustNotDeadlockWithValidExtension() throws Exception {
        Long memberId = createMember(100_000);
        Long spaceId = createSpace();
        LocalDateTime t14 = tomorrowAt(14, 0);
        LocalDateTime t15 = tomorrowAt(15, 0);
        LocalDateTime t16 = tomorrowAt(16, 0);

        // 예약 B: 14:00~15:00 (연장 대상)
        Long reservationB = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED, t14, t15);
        // 예약 A: 15:00~16:00 (취소 대상)
        Long reservationA = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED, t15, t16);

        CountDownLatch cancelReachedRefund = new CountDownLatch(1);
        CountDownLatch resumeCancel = new CountDownLatch(1);

        // 취소가 Reservation A UPDATE -> Member X 잠금 -> Slot A DELETE를 마친 후 creditService.refund 진입 시점에 대기
        doAnswer(invocation -> {
            cancelReachedRefund.countDown();
            assertThat(resumeCancel.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(creditService).refund(eq(memberId), eq(reservationA), anyInt());

        // 연장이 memberRepository.findByIdForUpdate 호출에 진입함을 관측하기 위한 프록시 설정
        CountDownLatch extendEnteredMemberLock = new CountDownLatch(1);
        MemberRepository originalRepo = (MemberRepository) ReflectionTestUtils.getField(
                extendService, "memberRepository");
        MemberRepository proxyRepo = (MemberRepository) Proxy.newProxyInstance(
                MemberRepository.class.getClassLoader(),
                new Class<?>[]{MemberRepository.class},
                (proxy, method, args) -> {
                    if ("findByIdForUpdate".equals(method.getName())
                            && Long.valueOf(memberId).equals(args[0])) {
                        extendEnteredMemberLock.countDown();
                    }
                    try {
                        return method.invoke(originalRepo, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
        ReflectionTestUtils.setField(extendService, "memberRepository", proxyRepo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread 1: 예약 A 취소 시작
            Future<Throwable> cancelFuture = executor.submit(() -> capture(() ->
                    cancelService.cancel(memberId, reservationA)));

            // 취소가 Member X 잠금을 쥐고 Slot DELETE를 완료한 뒤 환불 직전 단계에 도달했음을 확인
            assertThat(cancelReachedRefund.await(10, TimeUnit.SECONDS)).as("취소가 환불 단계에 도달해야 한다").isTrue();

            // Thread 2: 동일 회원의 다른 예약 B 연장 요청 (15:00~16:00 구간으로 연장 시도)
            Future<Throwable> extendFuture = executor.submit(() -> capture(() ->
                    extendService.extend(memberId, reservationB, t15, t16)));

            // 연장이 Member X 잠금 획득 호출에 진입했음을 확인 (취소가 Member X를 쥐고 있으므로 대기)
            assertThat(extendEnteredMemberLock.await(5, TimeUnit.SECONDS))
                    .as("연장이 회원 잠금 획득 호출에 진입해야 한다").isTrue();
            // 취소 트랜잭션이 아직 커밋되지 않았으므로 연장이 완료되지 못하고 대기 중임을 실증
            assertThat(extendFuture.isDone()).as("취소가 커밋되기 전에는 연장이 완료되지 않아야 한다").isFalse();

            // 취소 재개 및 커밋 허용 (Member 잠금 해제)
            resumeCancel.countDown();

            Throwable cancelFailure = cancelFuture.get(15, TimeUnit.SECONDS);
            Throwable extendFailure = extendFuture.get(15, TimeUnit.SECONDS);

            // 1. 취소는 데드락 없이 반드시 성공해야 함
            assertThat(cancelFailure).as("취소는 성공해야 한다").isNull();

            // 2. 연장은 취소가 슬롯을 반환하고 커밋한 후 데드락 없이 성공해야 함
            assertThat(extendFailure).as("연장은 데드락 없이 성공해야 한다").isNull();

            // 3. DB 정합성 검증
            // 예약 A: CANCELLED, 슬롯 0개, 환불 원장 1건
            assertThat(statusOf(reservationA)).isEqualTo(ReservationStatus.CANCELLED.name());
            assertThat(countSlots(reservationA)).isEqualTo(0);
            assertThat(countLedger(reservationA, "REFUND")).isEqualTo(1);

            // 예약 B: CONFIRMED, endTime 16:00, 슬롯 4개, 차감 원장 1건
            assertThat(statusOf(reservationB)).isEqualTo(ReservationStatus.CONFIRMED.name());
            assertThat(reservationRepository.findById(reservationB).orElseThrow().getEndTime()).isEqualTo(t16);
            assertThat(countSlots(reservationB)).isEqualTo(4);
            assertThat(countLedger(reservationB, "RESERVATION_CHARGE")).isEqualTo(1);

            // 회원 잔액: 100,000 (환불 +10,000, 연장 차감 -10,000)
            assertThat(balanceOf(memberId)).isEqualTo(100_000);
        } finally {
            ReflectionTestUtils.setField(extendService, "memberRepository", originalRepo);
            resumeCancel.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("동일 회원의 예약 취소(15~16시)와 동일 구간 대체 HOLD(15~16시) 동시 경합 시 데드락 없이 모두 정상 완료")
    void cancelAndHoldSameSlot_mustNotDeadlockWithValidHold() throws Exception {
        Long memberId = createMember(100_000);
        Long spaceId = createSpace();
        LocalDateTime t15 = tomorrowAt(15, 0);
        LocalDateTime t16 = tomorrowAt(16, 0);

        // 예약 A: 15:00~16:00 (취소 대상)
        Long reservationA = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED, t15, t16);

        CountDownLatch cancelReachedRefund = new CountDownLatch(1);
        CountDownLatch resumeCancel = new CountDownLatch(1);

        // 취소가 Reservation A UPDATE -> Member X 잠금 -> Slot A DELETE를 마친 후 creditService.refund 진입 시점에 대기
        doAnswer(invocation -> {
            cancelReachedRefund.countDown();
            assertThat(resumeCancel.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(creditService).refund(eq(memberId), eq(reservationA), anyInt());

        // HOLD가 memberRepository.findByIdForUpdate 호출에 진입함을 관측하기 위한 프록시 설정
        CountDownLatch holdEnteredMemberLock = new CountDownLatch(1);
        MemberRepository originalRepo = (MemberRepository) ReflectionTestUtils.getField(
                holdService, "memberRepository");
        MemberRepository proxyRepo = (MemberRepository) Proxy.newProxyInstance(
                MemberRepository.class.getClassLoader(),
                new Class<?>[]{MemberRepository.class},
                (proxy, method, args) -> {
                    if ("findByIdForUpdate".equals(method.getName())
                            && Long.valueOf(memberId).equals(args[0])) {
                        holdEnteredMemberLock.countDown();
                    }
                    try {
                        return method.invoke(originalRepo, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
        ReflectionTestUtils.setField(holdService, "memberRepository", proxyRepo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread 1: 예약 A 취소 시작
            Future<Throwable> cancelFuture = executor.submit(() -> capture(() ->
                    cancelService.cancel(memberId, reservationA)));

            // 취소가 Member X 잠금을 쥐고 Slot DELETE를 완료한 뒤 환불 단계에 도달했음을 확인
            assertThat(cancelReachedRefund.await(10, TimeUnit.SECONDS)).as("취소가 환불 단계에 도달해야 한다").isTrue();

            // Thread 2: 동일 회원의 동일 구간(15:00~16:00) 신규 HOLD 요청
            final ReservationResponse[] holdResponseHolder = new ReservationResponse[1];
            Future<Throwable> holdFuture = executor.submit(() -> capture(() -> {
                holdResponseHolder[0] = holdService.createHold(memberId, spaceId, t15, t16);
            }));

            // HOLD가 Member X 잠금 획득 호출에 진입했음을 확인 (취소가 Member X를 쥐고 있으므로 대기)
            assertThat(holdEnteredMemberLock.await(5, TimeUnit.SECONDS))
                    .as("HOLD가 회원 잠금 획득 호출에 진입해야 한다").isTrue();
            // 취소 트랜잭션이 아직 커밋되지 않았으므로 HOLD가 완료되지 못하고 대기 중임을 실증
            assertThat(holdFuture.isDone()).as("취소가 커밋되기 전에는 HOLD가 완료되지 않아야 한다").isFalse();

            // 취소 재개 및 커밋 허용 (Member 잠금 해제)
            resumeCancel.countDown();

            Throwable cancelFailure = cancelFuture.get(15, TimeUnit.SECONDS);
            Throwable holdFailure = holdFuture.get(15, TimeUnit.SECONDS);

            // 1. 취소는 데드락 없이 반드시 성공해야 함
            assertThat(cancelFailure).as("취소는 성공해야 한다").isNull();

            // 2. HOLD는 취소가 슬롯을 반환하고 커밋한 후 데드락 없이 정상 성공해야 함
            assertThat(holdFailure).as("대체 HOLD는 데드락 없이 성공해야 한다").isNull();
            assertThat(holdResponseHolder[0]).isNotNull();
            Long newHoldId = holdResponseHolder[0].reservationId();

            // 3. DB 정합성 검증
            // 예약 A: CANCELLED, 슬롯 0개, 환불 원장 1건
            assertThat(statusOf(reservationA)).isEqualTo(ReservationStatus.CANCELLED.name());
            assertThat(countSlots(reservationA)).isEqualTo(0);
            assertThat(countLedger(reservationA, "REFUND")).isEqualTo(1);

            // 신규 HOLD: HELD, 슬롯 2개
            assertThat(statusOf(newHoldId)).isEqualTo(ReservationStatus.HELD.name());
            assertThat(countSlots(newHoldId)).isEqualTo(2);

            // 회원 잔액: 110,000 (환불 +10,000)
            assertThat(balanceOf(memberId)).isEqualTo(110_000);
        } finally {
            ReflectionTestUtils.setField(holdService, "memberRepository", originalRepo);
            resumeCancel.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("다른 회원의 만료 HELD 관리자 강제 취소(Space X 선점)와 동일 구간 신규 HOLD 동시 경합 시 순서 고정 하에 양측 완료")
    void adminForceCancelExpiredHeldAndHoldSameSlot_mustNotDeadlockWithValidHold() throws Exception {
        Long memberId = createMember(100_000);
        Long adminMemberId = createMember(0);
        Long spaceId = createSpace();
        LocalDateTime t15 = tomorrowAt(15, 0);
        LocalDateTime t16 = tomorrowAt(16, 0);

        LocalDateTime now = now();
        // 만료된 HELD 예약 A (holdExpiresAt < now)
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .memberId(memberId)
                .spaceId(spaceId)
                .startTime(t15)
                .endTime(t16)
                .status(ReservationStatus.HELD)
                .pricePerSlotSnapshot(PRICE_PER_SLOT)
                .totalAmount(PRICE_PER_SLOT * 2)
                .holdExpiresAt(now.minusMinutes(1))
                .createdAt(now.minusMinutes(11))
                .build());
        for (LocalDateTime slot = t15; slot.isBefore(t16); slot = slot.plusMinutes(30)) {
            reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), spaceId, slot));
        }
        Long expiredHeldId = reservation.getId();

        CountDownLatch adminReachedHistory = new CountDownLatch(1);
        CountDownLatch resumeAdmin = new CountDownLatch(1);
        CountDownLatch holdEnteredSpace = new CountDownLatch(1);

        com.ovengers.slotkey.space.repository.SpaceRepository originalHoldSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) ReflectionTestUtils.getField(holdService, "spaceRepository");
        com.ovengers.slotkey.space.repository.SpaceRepository proxyHoldSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.space.repository.SpaceRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.space.repository.SpaceRepository.class},
                        (proxy, method, args) -> {
                            if ("findByIdForShare".equals(method.getName()) && Long.valueOf(spaceId).equals(args[0])) {
                                holdEnteredSpace.countDown();
                            }
                            try {
                                return method.invoke(originalHoldSpaceRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(holdService, "spaceRepository", proxyHoldSpaceRepo);

        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository originalAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) ReflectionTestUtils.getField(adminReservationService, "statusHistoryRepository");
        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository proxyAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class},
                        (proxy, method, args) -> {
                            if ("save".equals(method.getName())) {
                                adminReachedHistory.countDown();
                                if (!resumeAdmin.await(10, TimeUnit.SECONDS)) {
                                    throw new RuntimeException("admin resume timeout");
                                }
                            }
                            try {
                                return method.invoke(originalAdminHistoryRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", proxyAdminHistoryRepo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final ReservationResponse[] holdResponseHolder = new ReservationResponse[1];

            // Thread 1: 관리자 만료 HELD 강제 취소 시작 (Space X -> Reservation X 획득 후 이력 저장 직전 대기)
            Future<Throwable> adminFuture = executor.submit(() -> capture(() ->
                    adminReservationService.forceCancel(expiredHeldId, "관리자 만료 취소", adminMemberId)));

            assertThat(adminReachedHistory.await(10, TimeUnit.SECONDS))
                    .as("관리자 취소가 Space X 및 Reservation X 획득 후 이력 저장 단계에 도달해야 한다").isTrue();

            // Thread 2: 동일 구간 신규 대체 HOLD 요청
            Future<Throwable> holdFuture = executor.submit(() -> capture(() -> {
                holdResponseHolder[0] = holdService.createHold(memberId, spaceId, t15, t16);
            }));

            // HOLD가 Space 공유 잠금 획득 호출에 진입했음을 확인 (관리자가 Space X를 쥐고 있으므로 대기)
            assertThat(holdEnteredSpace.await(5, TimeUnit.SECONDS))
                    .as("HOLD가 Space 공유 잠금 획득 호출에 진입해야 한다").isTrue();
            // 관리자 트랜잭션이 아직 커밋되지 않았으므로 HOLD가 완료되지 못하고 대기 중임을 실증
            assertThat(holdFuture.isDone()).as("관리자가 커밋되기 전에는 HOLD가 완료되지 않아야 한다").isFalse();

            // 관리자 재개 및 커밋 (Space X 해제)
            resumeAdmin.countDown();

            Throwable adminFailure = adminFuture.get(15, TimeUnit.SECONDS);
            Throwable holdFailure = holdFuture.get(15, TimeUnit.SECONDS);

            // 두 스레드 모두 데드락 없이 완료되어야 함
            assertThat(adminFailure).as("관리자 강제 취소는 성공해야 한다").isNull();
            assertThat(holdFailure).as("대체 HOLD는 데드락 없이 성공해야 한다").isNull();

            // 최종 DB 상태 검증
            // 만료된 기존 예약: CANCELLED, 슬롯 0개
            assertThat(statusOf(expiredHeldId)).isEqualTo(ReservationStatus.CANCELLED.name());
            assertThat(countSlots(expiredHeldId)).isEqualTo(0);

            // 신규 HOLD: HELD, 슬롯 2개
            assertThat(holdResponseHolder[0]).isNotNull();
            Long newHoldId = holdResponseHolder[0].reservationId();
            assertThat(statusOf(newHoldId)).isEqualTo(ReservationStatus.HELD.name());
            assertThat(countSlots(newHoldId)).isEqualTo(2);

            // 회원 잔액: 100,000 (HELD는 환불이 없으므로 불변)
            assertThat(balanceOf(memberId)).isEqualTo(100_000);
        } finally {
            ReflectionTestUtils.setField(holdService, "spaceRepository", originalHoldSpaceRepo);
            ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", originalAdminHistoryRepo);
            resumeAdmin.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("관리자 자신의 만료 HELD 강제 취소와 신규 HOLD 경합 시 Space X 선점으로 이력 FK S-lock 데드락 없이 양측 완료")
    void adminForceCancelOwnExpiredHeldAndHoldSameSlot_mustNotDeadlockWithValidHold() throws Exception {
        // 관리자 자신이 예약자인 케이스 (adminMemberId == memberId)
        Long adminMemberId = createMember(100_000);
        Long spaceId = createSpace();
        LocalDateTime t15 = tomorrowAt(15, 0);
        LocalDateTime t16 = tomorrowAt(16, 0);

        LocalDateTime now = now();
        // 관리자 자신의 만료된 HELD 예약 A
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .memberId(adminMemberId)
                .spaceId(spaceId)
                .startTime(t15)
                .endTime(t16)
                .status(ReservationStatus.HELD)
                .pricePerSlotSnapshot(PRICE_PER_SLOT)
                .totalAmount(PRICE_PER_SLOT * 2)
                .holdExpiresAt(now.minusMinutes(1))
                .createdAt(now.minusMinutes(11))
                .build());
        for (LocalDateTime slot = t15; slot.isBefore(t16); slot = slot.plusMinutes(30)) {
            reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), spaceId, slot));
        }
        Long expiredHeldId = reservation.getId();

        CountDownLatch adminReachedHistory = new CountDownLatch(1);
        CountDownLatch resumeAdmin = new CountDownLatch(1);
        CountDownLatch holdEnteredSpace = new CountDownLatch(1);

        com.ovengers.slotkey.space.repository.SpaceRepository originalHoldSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) ReflectionTestUtils.getField(holdService, "spaceRepository");
        com.ovengers.slotkey.space.repository.SpaceRepository proxyHoldSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.space.repository.SpaceRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.space.repository.SpaceRepository.class},
                        (proxy, method, args) -> {
                            if ("findByIdForShare".equals(method.getName()) && Long.valueOf(spaceId).equals(args[0])) {
                                holdEnteredSpace.countDown();
                            }
                            try {
                                return method.invoke(originalHoldSpaceRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(holdService, "spaceRepository", proxyHoldSpaceRepo);

        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository originalAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) ReflectionTestUtils.getField(adminReservationService, "statusHistoryRepository");
        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository proxyAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class},
                        (proxy, method, args) -> {
                            if ("save".equals(method.getName())) {
                                adminReachedHistory.countDown();
                                if (!resumeAdmin.await(10, TimeUnit.SECONDS)) {
                                    throw new RuntimeException("admin resume timeout");
                                }
                            }
                            try {
                                return method.invoke(originalAdminHistoryRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", proxyAdminHistoryRepo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final ReservationResponse[] holdResponseHolder = new ReservationResponse[1];

            // Thread 1: 관리자 자신의 만료 HELD 강제 취소 시작 (Space X -> Reservation X 획득 후 이력 저장 직전 대기)
            Future<Throwable> adminFuture = executor.submit(() -> capture(() ->
                    adminReservationService.forceCancel(expiredHeldId, "자기 예약 취소", adminMemberId)));

            assertThat(adminReachedHistory.await(10, TimeUnit.SECONDS))
                    .as("관리자 취소가 Space X 및 Reservation X 획득 후 이력 저장 단계에 도달해야 한다").isTrue();

            // Thread 2: 동일 회원(관리자 본인)의 동일 구간 신규 HOLD 요청
            Future<Throwable> holdFuture = executor.submit(() -> capture(() -> {
                holdResponseHolder[0] = holdService.createHold(adminMemberId, spaceId, t15, t16);
            }));

            // HOLD가 Space 공유 잠금 획득 호출에 진입했음을 확인 (관리자가 Space X를 쥐고 있으므로 대기)
            assertThat(holdEnteredSpace.await(5, TimeUnit.SECONDS))
                    .as("HOLD가 Space 공유 잠금 획득 호출에 진입해야 한다").isTrue();
            assertThat(holdFuture.isDone()).as("관리자가 커밋되기 전에는 HOLD가 완료되지 않아야 한다").isFalse();

            // 관리자 재개 및 커밋 (이력 저장 시 changedBy=adminMemberId FK Member S-lock 획득 -> 정상 커밋 및 Space X 해제)
            resumeAdmin.countDown();

            Throwable adminFailure = adminFuture.get(15, TimeUnit.SECONDS);
            Throwable holdFailure = holdFuture.get(15, TimeUnit.SECONDS);

            // 두 스레드 모두 데드락 없이 완료되어야 함
            assertThat(adminFailure).as("관리자 자기 예약 강제 취소는 성공해야 한다").isNull();
            assertThat(holdFailure).as("대체 HOLD는 데드락 없이 성공해야 한다").isNull();

            // 최종 DB 상태 검증
            // 만료된 기존 예약: CANCELLED, 슬롯 0개
            assertThat(statusOf(expiredHeldId)).isEqualTo(ReservationStatus.CANCELLED.name());
            assertThat(countSlots(expiredHeldId)).isEqualTo(0);

            // 신규 HOLD: HELD, 슬롯 2개
            assertThat(holdResponseHolder[0]).isNotNull();
            Long newHoldId = holdResponseHolder[0].reservationId();
            assertThat(statusOf(newHoldId)).isEqualTo(ReservationStatus.HELD.name());
            assertThat(countSlots(newHoldId)).isEqualTo(2);

            // 회원 잔액: 100,000 불변
            assertThat(balanceOf(adminMemberId)).isEqualTo(100_000);
        } finally {
            ReflectionTestUtils.setField(holdService, "spaceRepository", originalHoldSpaceRepo);
            ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", originalAdminHistoryRepo);
            resumeAdmin.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("동일 회원의 만료 HELD 관리자 강제 취소(Space X 선점)와 동일 구간 다른 예약 연장 동시 경합 시 순서 고정 하에 양측 완료")
    void adminForceCancelExpiredHeldAndExtendOtherReservation_mustNotDeadlockWithValidExtension() throws Exception {
        Long memberId = createMember(100_000);
        Long adminMemberId = createMember(0);
        Long spaceId = createSpace();
        LocalDateTime t14 = tomorrowAt(14, 0);
        LocalDateTime t15 = tomorrowAt(15, 0);
        LocalDateTime t16 = tomorrowAt(16, 0);

        LocalDateTime now = now();
        // 예약 B: 14:00~15:00 (연장 대상)
        Long reservationB = createReservation(memberId, spaceId, ReservationStatus.CONFIRMED, t14, t15);

        // 만료된 HELD 예약 A: 15:00~16:00 (강제 취소 대상)
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .memberId(memberId)
                .spaceId(spaceId)
                .startTime(t15)
                .endTime(t16)
                .status(ReservationStatus.HELD)
                .pricePerSlotSnapshot(PRICE_PER_SLOT)
                .totalAmount(PRICE_PER_SLOT * 2)
                .holdExpiresAt(now.minusMinutes(1))
                .createdAt(now.minusMinutes(11))
                .build());
        for (LocalDateTime slot = t15; slot.isBefore(t16); slot = slot.plusMinutes(30)) {
            reservationSlotRepository.save(ReservationSlot.of(reservation.getId(), spaceId, slot));
        }
        Long expiredHeldId = reservation.getId();

        CountDownLatch adminReachedHistory = new CountDownLatch(1);
        CountDownLatch resumeAdmin = new CountDownLatch(1);
        CountDownLatch extendEnteredSpace = new CountDownLatch(1);

        com.ovengers.slotkey.space.repository.SpaceRepository originalExtendSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) ReflectionTestUtils.getField(extendService, "spaceRepository");
        com.ovengers.slotkey.space.repository.SpaceRepository proxyExtendSpaceRepo =
                (com.ovengers.slotkey.space.repository.SpaceRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.space.repository.SpaceRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.space.repository.SpaceRepository.class},
                        (proxy, method, args) -> {
                            if ("findByIdForShare".equals(method.getName()) && Long.valueOf(spaceId).equals(args[0])) {
                                extendEnteredSpace.countDown();
                            }
                            try {
                                return method.invoke(originalExtendSpaceRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(extendService, "spaceRepository", proxyExtendSpaceRepo);

        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository originalAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) ReflectionTestUtils.getField(adminReservationService, "statusHistoryRepository");
        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository proxyAdminHistoryRepo =
                (com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository) Proxy.newProxyInstance(
                        com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class.getClassLoader(),
                        new Class<?>[]{com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository.class},
                        (proxy, method, args) -> {
                            if ("save".equals(method.getName())) {
                                adminReachedHistory.countDown();
                                if (!resumeAdmin.await(10, TimeUnit.SECONDS)) {
                                    throw new RuntimeException("admin resume timeout");
                                }
                            }
                            try {
                                return method.invoke(originalAdminHistoryRepo, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                );
        ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", proxyAdminHistoryRepo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> adminFuture = executor.submit(() -> capture(() ->
                    adminReservationService.forceCancel(expiredHeldId, "관리자 만료 취소", adminMemberId)));

            assertThat(adminReachedHistory.await(10, TimeUnit.SECONDS))
                    .as("관리자 취소가 Space X 및 Reservation X 획득 후 이력 저장 단계에 도달해야 한다").isTrue();

            Future<Throwable> extendFuture = executor.submit(() -> capture(() ->
                    extendService.extend(memberId, reservationB, t15, t16)));

            assertThat(extendEnteredSpace.await(5, TimeUnit.SECONDS))
                    .as("연장이 Space 공유 잠금 획득 호출에 진입해야 한다").isTrue();
            assertThat(extendFuture.isDone()).as("관리자가 커밋되기 전에는 연장이 완료되지 않아야 한다").isFalse();

            resumeAdmin.countDown();

            Throwable adminFailure = adminFuture.get(15, TimeUnit.SECONDS);
            Throwable extendFailure = extendFuture.get(15, TimeUnit.SECONDS);

            // 관리자 강제 취소와 연장 모두 정상 성공
            assertThat(adminFailure).as("관리자 강제 취소는 성공해야 한다").isNull();
            assertThat(extendFailure).as("연장은 데드락 없이 성공해야 한다").isNull();

            // 최종 DB 상태 검증
            // 만료된 기존 예약: CANCELLED, 슬롯 0개
            assertThat(statusOf(expiredHeldId)).isEqualTo(ReservationStatus.CANCELLED.name());
            assertThat(countSlots(expiredHeldId)).isEqualTo(0);

            // 예약 B: CONFIRMED, 슬롯 4개, 차감 원장 1건
            assertThat(statusOf(reservationB)).isEqualTo(ReservationStatus.CONFIRMED.name());
            assertThat(countSlots(reservationB)).isEqualTo(4);
            assertThat(countLedger(reservationB, "RESERVATION_CHARGE")).isEqualTo(1);

            // 회원 잔액: 90,000 (연장 차감 -10,000, HELD는 환불 없음)
            assertThat(balanceOf(memberId)).isEqualTo(90_000);
        } finally {
            ReflectionTestUtils.setField(extendService, "spaceRepository", originalExtendSpaceRepo);
            ReflectionTestUtils.setField(adminReservationService, "statusHistoryRepository", originalAdminHistoryRepo);
            resumeAdmin.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Throwable capture(Runnable task) {
        try {
            task.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
