package com.ovengers.slotkey.access.authorization;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DoorAccessAuthorizationService {

    // 로그인 회원과 예약 회원이 같은지 확인
    public boolean isOwner(
            Long loginMemberId,
            Long reservationMemberId
    ) {
        return Objects.equals(
                loginMemberId,
                reservationMemberId
        );
    }

    // 로그인 회원이 예약 소유자인지 검증
    public void validateOwner(
            Long loginMemberId,
            Long reservationMemberId
    ) {
        if (!isOwner(loginMemberId, reservationMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
    }
}