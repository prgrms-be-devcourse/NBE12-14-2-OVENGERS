package com.ovengers.slotkey.access.dto.response;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DoorAccessVerifyResponse {

    private AccessResult result;
    private AccessDenyReason reasonCode;
    private String spaceName;
    private Boolean firstCheckIn;
    private LocalDateTime attemptedAt;

    // 출입 허용 응답 생성
    public static DoorAccessVerifyResponse allow(
            String spaceName,
            boolean firstCheckIn,
            LocalDateTime attemptedAt
    ) {
        return new DoorAccessVerifyResponse(
                AccessResult.ALLOW,
                null,
                spaceName,
                firstCheckIn,
                attemptedAt
        );
    }

    // 출입 거절 응답 생성
    public static DoorAccessVerifyResponse deny(
            AccessDenyReason reasonCode,
            LocalDateTime attemptedAt
    ) {
        return new DoorAccessVerifyResponse(
                AccessResult.DENY,
                reasonCode,
                null,
                null,
                attemptedAt
        );
    }
}
