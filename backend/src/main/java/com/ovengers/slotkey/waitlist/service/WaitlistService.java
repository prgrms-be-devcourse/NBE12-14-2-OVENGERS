package com.ovengers.slotkey.waitlist.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.policy.ReservationTimePolicy;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.waitlist.dto.response.WaitlistEntryResponse;
import com.ovengers.slotkey.waitlist.entity.WaitlistEntry;
import com.ovengers.slotkey.waitlist.entity.WaitlistStatus;
import com.ovengers.slotkey.waitlist.repository.WaitlistEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 대기 신청·취소·내 목록(1단계, docs/plans/waitlist-outbox/01-waitlist-rules.md).
 *
 * 신청 검사 순서: 공간 -> 구간(기존 예약 시간 정책 재사용) -> 회원 상태 -> 점유 여부
 * -> 본인 점유 -> 활성 건수 -> UNIQUE(중복). 앞의 검사가 뒤의 검사보다 사용자가 바로 고칠 수 있는
 * 문제이기 때문이다(예: 3건이 찬 상태로 같은 구간을 또 신청하면 LIMIT_EXCEEDED가 먼저 나온다).
 *
 * "활성 대기"의 기준은 세 곳(3건 제한, 신청 순서, 내 목록)에서 모두 같다 —
 * `status = WAITING AND startTime > now`. 시작 시각 기준인 이유는 4단계 알림 조건과
 * `ReservationTimePolicy`가 시작 시각을 본다는 점, 그리고 `EXPIRED` 전이가 4단계 범위라
 * 지난 구간의 WAITING 행이 남더라도 제한을 차지하지 않아야 한다는 점이다(step-1-review.md H1).
 *
 * 점유 여부 검사와 INSERT 사이의 경합은 수용한다(01 문서 §1). 대기 신청은 돈이 움직이지 않고
 * 자리를 잡지도 않으므로, 예약 경로처럼 슬롯 UNIQUE로 판정할 필요가 없다 —
 * 알림 시점(4단계)에 같은 조건을 다시 검사하는 것이 최종 방어선이다.
 *
 * 신규 코드이므로 Lombok을 쓰지 않는다(AGENTS.md).
 */
@Service
@Transactional(readOnly = true)
public class WaitlistService {

    /** 활성 대기 최대 건수. 애플리케이션 검사이므로 동시 요청 시 초과를 수용한다. */
    private static final int MAX_ACTIVE_ENTRIES = 3;

    /** V17의 활성 UNIQUE 제약명. 제약 위반을 중복으로 볼지 가리는 기준이다. */
    private static final String ACTIVE_UNIQUE_CONSTRAINT = "uk_waitlist_entry_member_space_range_active";

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SpaceRepository spaceRepository;
    private final MemberRepository memberRepository;
    private final ReservationOccupancyProvider reservationOccupancyProvider;
    private final Clock clock;

    public WaitlistService(WaitlistEntryRepository waitlistEntryRepository,
                           SpaceRepository spaceRepository,
                           MemberRepository memberRepository,
                           ReservationOccupancyProvider reservationOccupancyProvider,
                           Clock clock) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.spaceRepository = spaceRepository;
        this.memberRepository = memberRepository;
        this.reservationOccupancyProvider = reservationOccupancyProvider;
        this.clock = clock;
    }

    @Transactional
    public WaitlistEntryResponse apply(Long memberId, Long spaceId,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now(clock);

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
        if (space.getStatus() != SpaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SPACE_INACTIVE);
        }

        ReservationTimePolicy.validate(startTime, endTime, space.getOpeningTime(), space.getClosingTime(), now);

        validateMemberCanApply(memberId);

        if (!reservationOccupancyProvider.hasOccupiedSlot(spaceId, startTime, endTime, now)) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_NEEDED);
        }
        if (reservationOccupancyProvider.hasOwnOccupyingReservationOverlapping(
                memberId, spaceId, startTime, endTime, now)) {
            throw new BusinessException(ErrorCode.WAITLIST_ALREADY_HOLDING);
        }
        if (waitlistEntryRepository.countByMemberIdAndStatusAndStartTimeAfter(
                memberId, WaitlistStatus.WAITING, now) >= MAX_ACTIVE_ENTRIES) {
            throw new BusinessException(ErrorCode.WAITLIST_LIMIT_EXCEEDED);
        }

        WaitlistEntry entry = WaitlistEntry.waiting(memberId, spaceId, startTime, endTime, now);
        WaitlistEntry saved;
        try {
            // UNIQUE(member_id, space_id, start_time, end_time, active) 위반을 여기서 도메인 예외로 바꾼다.
            // active가 WAITING에서만 1이므로 이 위반은 "활성 신청이 이미 있다"는 뜻이고,
            // 취소된 이력 행(active IS NULL)은 재신청을 막지 않는다.
            // flush를 미루면 트랜잭션 커밋 시점에 터져 이 catch를 지나친다.
            saved = waitlistEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            if (!isActiveUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException(ErrorCode.WAITLIST_DUPLICATE);
        }

        return WaitlistEntryResponse.of(saved, positionOf(saved, now));
    }

    /**
     * 본인 대기 취소. 이미 취소된 대기에 대한 재요청은 그대로 성공으로 둔다(멱등) —
     * 취소는 돈이나 자리를 움직이지 않으므로 상태 충돌로 막을 이유가 없다.
     */
    @Transactional
    public void cancel(Long memberId, Long entryId) {
        WaitlistEntry entry = waitlistEntryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
        if (!Objects.equals(entry.getMemberId(), memberId)) {
            // 관리자도 타인의 대기를 취소할 수 없다(관리자 권한 != 소유권, AGENTS.md 규칙 4).
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
        entry.cancel();
    }

    /**
     * 활성 대기만, 신청 순서대로. 활성의 기준은 신청 제한·순서 계산과 같은
     * `status = WAITING AND startTime > now`다. position은 안내용이며 자리를 보장하지 않는다.
     */
    public List<WaitlistEntryResponse> getMyEntries(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return waitlistEntryRepository
                .findAllByMemberIdAndStatusAndStartTimeAfterOrderByIdAsc(
                        memberId, WaitlistStatus.WAITING, now).stream()
                .map(entry -> WaitlistEntryResponse.of(entry, positionOf(entry, now)))
                .toList();
    }

    private void validateMemberCanApply(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    /**
     * 활성 UNIQUE 위반인지 판정한다. 이 테이블에는 CHECK 제약도 둘 있어(`chk_waitlist_entry_active`,
     * `chk_waitlist_entry_time_range`) 제약 위반 전부를 `WAITLIST_DUPLICATE`로 바꾸면 4단계에서
     * 진짜 원인이 409 "이미 신청한 대기입니다"에 가려진다(step-1-review.md M2).
     *
     * 판정은 예외 연쇄의 메시지에서 제약명을 찾는 방식이다. Hibernate의 `ConstraintViolationException`
     * 생성자·제약명 추출은 버전에 따라 달라지는데, 제약명이 담긴 드라이버 메시지는 MySQL에서 안정적이고
     * 이 레포의 테스트·운영 DB가 모두 MySQL 8.4이기 때문이다. 제약명이 안 보이면 중복으로 보지 않고
     * 그대로 전파해 500으로 드러낸다 — 원인을 가리는 쪽보다 시끄러운 쪽이 안전하다.
     */
    static boolean isActiveUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(ACTIVE_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private long positionOf(WaitlistEntry entry, LocalDateTime now) {
        return waitlistEntryRepository.countOverlappingAhead(
                entry.getSpaceId(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getId(),
                WaitlistStatus.WAITING,
                now) + 1;
    }
}
