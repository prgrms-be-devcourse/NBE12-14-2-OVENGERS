package com.ovengers.slotkey.space.authorization;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceAuthorizationServiceTest {

    private final SpaceAuthorizationService service = new SpaceAuthorizationService();

    @Test
    @DisplayName("ADMIN principal이면 예외 없이 통과한다")
    void validateCanManageSpace_admin_passes() {
        AuthPrincipal adminPrincipal = new AuthPrincipal(1L, "admin@test.com", MemberRole.ADMIN);

        assertThatCode(() -> service.validateCanManageSpace(adminPrincipal))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("USER principal이면 ACCESS_DENIED BusinessException이 발생한다")
    void validateCanManageSpace_user_throwsAccessDenied() {
        AuthPrincipal userPrincipal = new AuthPrincipal(2L, "user@test.com", MemberRole.USER);

        assertThatThrownBy(() -> service.validateCanManageSpace(userPrincipal))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("null principal이면 ACCESS_DENIED BusinessException이 발생한다")
    void validateCanManageSpace_null_throwsAccessDenied() {
        assertThatThrownBy(() -> service.validateCanManageSpace(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
