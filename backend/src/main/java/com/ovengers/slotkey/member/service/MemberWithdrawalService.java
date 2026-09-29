package com.ovengers.slotkey.member.service;

import com.ovengers.slotkey.access.repository.DoorAccessTokenRepository;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.credit.service.CreditExpirationService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
    private final CreditExpirationService creditExpirationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DoorAccessTokenRepository doorAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void withdraw(Long memberId, String currentPassword) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED)
                );

        if (member.getRole() != MemberRole.USER) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN_ROLE,
                    "관리자 계정은 회원탈퇴 대상이 아닙니다."
            );
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        if (!passwordEncoder.matches(
                currentPassword,
                member.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.CURRENT_PASSWORD_MISMATCH
            );
        }

        boolean hasActiveReservation =
                reservationRepository.existsByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                ReservationStatus.HELD,
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.IN_USE
                        )
                );

        if (hasActiveReservation) {
            throw new BusinessException(
                    ErrorCode.WITHDRAWAL_ACTIVE_RESERVATION
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);

        creditExpirationService.expireForWithdrawal(member, now);

        member.withdraw(now);

        refreshTokenRepository.revokeAllByMemberId(memberId, now);

        doorAccessTokenRepository.revokeAllByMemberId(
                memberId,
                now,
                "MEMBER_WITHDRAWAL"
        );
    }
}