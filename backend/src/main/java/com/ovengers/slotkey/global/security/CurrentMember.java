package com.ovengers.slotkey.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 붙여 현재 인증된 회원(AuthPrincipal)을 주입받는다.
 * 실제 주입은 CurrentMemberArgumentResolver가 처리한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}
