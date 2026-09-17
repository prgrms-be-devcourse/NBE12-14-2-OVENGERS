package com.ovengers.slotkey.space.authorization;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceAuthorizationServiceTest {

    private SpaceAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new SpaceAuthorizationService();
    }

    @Test
    @DisplayName("ADMIN 역할의 AuthPrincipal은 공간 관리 권한 검증을 정상 통과한다")
    void validateCanManageSpace_admin_success() {
        // given
        AuthPrincipal adminPrincipal = new AuthPrincipal(1L, "admin@test.com", MemberRole.ADMIN);

        // when & then
        assertThatCode(() -> authorizationService.validateCanManageSpace(adminPrincipal))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("USER 역할의 AuthPrincipal은 ACCESS_DENIED 예외가 발생한다")
    void validateCanManageSpace_user_throwsAccessDenied() {
        // given
        AuthPrincipal userPrincipal = new AuthPrincipal(2L, "user@test.com", MemberRole.USER);

        // when & then
        assertThatThrownBy(() -> authorizationService.validateCanManageSpace(userPrincipal))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("null principal은 ACCESS_DENIED 예외가 발생한다")
    void validateCanManageSpace_nullPrincipal_throwsAccessDenied() {
        // when & then
        assertThatThrownBy(() -> authorizationService.validateCanManageSpace(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }
}
