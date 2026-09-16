package com.ovengers.slotkey.global.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentMember AuthPrincipal principal} 파라미터에 SecurityContext의 인증 정보를 주입한다.
 * CustomAuthenticationFilter가 요청 시점에 이미 AuthPrincipal을 Authentication의 principal로
 * 등록해두므로 여기서는 꺼내기만 한다. 인증되지 않은 요청은 SecurityConfig에서 이미 401로
 * 막히므로, 이 리졸버까지 도달했다면 인증된 상태임이 보장된다.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && parameter.getParameterType().equals(AuthPrincipal.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
