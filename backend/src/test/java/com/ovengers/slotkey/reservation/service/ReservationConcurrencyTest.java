package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시 예약 요청에 대한 슬롯 유니크 제약 검증(§9, 성공 기준 DoD). 실제 MySQL(Testcontainers)에서
 * 같은 공간·시간에 20개 요청을 동시에 보내 정확히 1건만 성공하는지 확인한다.
 * Docker가 필요하므로 로컬에서 Docker Desktop을 켠 뒤 실행한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class ReservationConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private ReservationHoldService reservationHoldService;
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

    private Long spaceId;
    private List<Long> memberIds;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        reservationSlotRepository.deleteAll();
        reservationStatusHistoryRepository.deleteAll();
        reservationRepository.deleteAll();

        Space space = spaceRepository.save(Space.builder()
                .name("동시성 테스트 공간")
                .location("서울")
                .capacity(10)
                .pricePerSlot(5000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build());
        spaceId = space.getId();

        memberIds = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            Member member = new Member(
                    "concurrency-" + i + "-" + System.nanoTime() + "@slotkey.test",
                    "{noop}password",
                    "동시성테스트" + i);
            memberIds.add(memberRepository.save(member).getId());
        }

        startTime = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0).withSecond(0).withNano(0);
        endTime = startTime.plusHours(1);
    }

    @AfterEach
    void tearDown() {
        reservationSlotRepository.deleteAll();
        reservationStatusHistoryRepository.deleteAll();
        reservationRepository.deleteAll();
        memberIds.forEach(memberRepository::deleteById);
        spaceRepository.deleteById(spaceId);
    }

    @Test
    @DisplayName("같은 공간·시간에 20개 요청이 동시에 들어와도 정확히 1건만 성공하고, 나머지는 슬롯 충돌로 실패하며 예약·슬롯 데이터가 남지 않는다")
    void createHold_concurrentRequests_onlyOneSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (Long memberId : memberIds) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    reservationHoldService.createHold(memberId, spaceId, startTime, endTime);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.RESERVATION_SLOT_CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(THREAD_COUNT - 1);

        assertThat(reservationRepository.count()).isEqualTo(1L);
        List<ReservationSlot> slots =
                reservationSlotRepository.findAllBySpaceIdAndSlotStartBetween(spaceId, startTime, endTime);
        assertThat(slots).hasSize(2); // 1시간 = 30분 슬롯 2개, 성공한 예약 1건 몫만 남는다
    }
}
