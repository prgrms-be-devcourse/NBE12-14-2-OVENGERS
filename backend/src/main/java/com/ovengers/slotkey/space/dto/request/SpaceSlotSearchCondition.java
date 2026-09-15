package com.ovengers.slotkey.space.dto.request;

import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.validation.constraints.NotNull;

public record SpaceSlotSearchCondition(
        @NotNull(message = "공간 상태를 입력해주세요.")
        SpaceStatus status
) {
}
