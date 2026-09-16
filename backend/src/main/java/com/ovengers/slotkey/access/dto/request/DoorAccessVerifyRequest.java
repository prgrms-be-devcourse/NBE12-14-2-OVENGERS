package com.ovengers.slotkey.access.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DoorAccessVerifyRequest {

    @NotNull(message = "공간 ID는 필수 입니다.")
    private Long spaceId;

    @NotBlank(message = "출입 토큰은 필수입니다.")
    private String token;
}
