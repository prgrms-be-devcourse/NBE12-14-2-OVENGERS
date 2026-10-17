
package com.ovengers.slotkey.reservation.repository;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("회원별 전체 예약 건수 Repository 통합 테스트")
class ReservationMemberCountRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Test
    @DisplayName("예약 상태와 관계없이 해당 회원의 예약만 집계한다")
    void countByMemberId_includesAllStatusesAndExcludesOtherMembers() {
        // given
        Member member = memberRepository.save(
                new Member("count-user@test.com", "조회대상")
        );

        Member otherMember = memberRepository.save(
                new Member("count-other@test.com", "다른회원")
        );

        Space space = spaceRepository.save(
                Space.builder()
                        .name("테스트 회의실")
                        .location("서울")
                        .capacity(4)
                        .pricePerSlot(3000L)
                        .openingTime(LocalTime.of(9, 0))
                        .closingTime(LocalTime.of(22, 0))
                        .status(SpaceStatus.ACTIVE)
                        .build()
        );

        // 조회 대상 회원의 예약 3건
        saveReservation(member.getId(), space.getId(),
                ReservationStatus.HELD, 10);

        saveReservation(member.getId(), space.getId(),
                ReservationStatus.CONFIRMED, 11);

        saveReservation(member.getId(), space.getId(),
                ReservationStatus.CANCELLED, 12);

        // 다른 회원의 예약 1건
        saveReservation(otherMember.getId(), space.getId(),
                ReservationStatus.CONFIRMED, 13);

        // when
        long count = reservationRepository.countByMemberId(
                member.getId()
        );

        // then
        assertThat(count).isEqualTo(3L);
    }


    private void saveReservation(
            Long memberId,
            Long spaceId,
            ReservationStatus status,
            int hour
    ) {
        LocalDateTime startTime =
                LocalDateTime.of(2026, 10, 20, hour, 0);

        LocalDateTime createdAt = startTime.minusDays(1);

        reservationRepository.save(
                Reservation.builder()
                        .memberId(memberId)
                        .spaceId(spaceId)
                        .startTime(startTime)
                        .endTime(startTime.plusMinutes(30))
                        .status(status)
                        .pricePerSlotSnapshot(3000)
                        .totalAmount(3000)
                        .createdAt(createdAt)
                        .holdExpiresAt(
                                status == ReservationStatus.HELD
                                        ? createdAt.plusMinutes(10)
                                        : null
                        )
                        .cancelledAt(
                                status == ReservationStatus.CANCELLED
                                        ? createdAt.plusMinutes(5)
                                        : null
                        )
                        .build()
        );
    }

}
