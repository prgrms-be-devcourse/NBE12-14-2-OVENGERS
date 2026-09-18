package com.ovengers.slotkey.access.service;

import com.ovengers.slotkey.access.dto.request.DoorAccessVerifyRequest;
import com.ovengers.slotkey.access.dto.response.DoorAccessVerifyResponse;
import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.DoorAccessToken;
import com.ovengers.slotkey.access.policy.DoorAccessTimePolicy;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoorAccessVerificationService {

    private final DoorAccessTokenService doorAccessTokenService;
    private final DoorAccessLogService doorAccessLogService;
    private final DoorAccessTimePolicy doorAccessTimePolicy;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final Clock clock;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    // 제출된 출입 토큰 검증
    @Transactional
    public DoorAccessVerifyResponse verify(
            Long loginMemberId,
            DoorAccessVerifyRequest request
    ) {
        LocalDateTime attemptedAt =
                LocalDateTime.now(clock);

        // 실제 출입을 시도한 로그인 회원 조회
        Member actorMember = memberRepository.findById(loginMemberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));

        Space requestedSpace = spaceRepository.findById(request.getSpaceId())
                .orElse(null);

        DoorAccessToken accessToken;

        try {
            accessToken =
                    doorAccessTokenService.findByRawToken(
                            request.getToken()
                    );
        } catch (BusinessException exception) {
            if (exception.getErrorCode()
                    != ErrorCode.ACCESS_TOKEN_NOT_FOUND) {
                throw exception;
            }

            return deny(
                    actorMember,
                    null,
                    requestedSpace,
                    AccessDenyReason.TOKEN_NOT_FOUND,
                    attemptedAt
            );
        }

        Reservation reservation =
                accessToken.getReservation();

        // 로그인 회원과 출입 토큰의 예약자가 같은지 확인
        if (!Objects.equals(
                loginMemberId,
                reservation.getMemberId()
        )) {
            return deny(
                    actorMember,
                    reservation,
                    requestedSpace,
                    AccessDenyReason.MEMBER_MISMATCH,
                    attemptedAt
            );
        }

        // 폐기된 토큰이면 출입 거절
        if (accessToken.isRevoked()) {
            return deny(
                    actorMember,
                    reservation,
                    requestedSpace,
                    AccessDenyReason.TOKEN_REVOKED,
                    attemptedAt
            );
        }

        // 출입 가능한 예약 상태인지 확인
        if (!isActiveReservation(reservation)) {
            return deny(
                    actorMember,
                    reservation,
                    requestedSpace,
                    AccessDenyReason.RESERVATION_NOT_ACTIVE,
                    attemptedAt
            );
        }

        // 요청한 공간과 예약 공간이 같은지 확인
        if (!Objects.equals(
                reservation.getSpaceId(),
                request.getSpaceId()
        )) {
            return deny(
                    actorMember,
                    reservation,
                    requestedSpace,
                    AccessDenyReason.SPACE_MISMATCH,
                    attemptedAt
            );
        }

        // 출입 가능한 시간인지 확인
        Optional<AccessDenyReason> timeDenyReason =
                doorAccessTimePolicy.findDenyReason(
                        attemptedAt,
                        reservation.getStartTime(),
                        reservation.getEndTime(),
                        reservation.getCheckedInAt()
                );

        if (timeDenyReason.isPresent()) {
            return deny(
                    actorMember,
                    reservation,
                    requestedSpace,
                    timeDenyReason.get(),
                    attemptedAt
            );
        }

        // 상태를 변경하기 전에 최초 체크인 여부를 저장한다.
        boolean firstCheckIn =
                reservation.getStatus() == ReservationStatus.CONFIRMED;

        // 최초 체크인 성공 시 예약 상태와 이력을 함께 변경한다.
        if (firstCheckIn) {
            reservation.checkIn(attemptedAt);

            reservationStatusHistoryRepository.save(
                    ReservationStatusHistory.of(
                            reservation.getId(),
                            loginMemberId,
                            ReservationStatus.CONFIRMED,
                            ReservationStatus.IN_USE,
                            "FIRST_CHECK_IN",
                            attemptedAt
                    )
            );
        }

        doorAccessLogService.createAllowLog(
                actorMember,
                reservation,
                requestedSpace,
                attemptedAt
        );

        return DoorAccessVerifyResponse.allow(
                requestedSpace.getName(),
                firstCheckIn,
                attemptedAt
        );
    }

    // 출입 가능한 예약 상태 확인
    private boolean isActiveReservation(
            Reservation reservation
    ) {
        return reservation.getStatus()
                == ReservationStatus.CONFIRMED
                || reservation.getStatus()
                == ReservationStatus.IN_USE;
    }

    // 출입 거절 로그 저장 및 응답 생성
    private DoorAccessVerifyResponse deny(
            Member actorMember,
            Reservation reservation,
            Space requestedSpace,
            AccessDenyReason reasonCode,
            LocalDateTime attemptedAt
    ) {
        doorAccessLogService.createDenyLog(
                actorMember,
                reservation,
                requestedSpace,
                reasonCode,
                attemptedAt
        );

        return DoorAccessVerifyResponse.deny(
                reasonCode,
                attemptedAt
        );
    }
}
