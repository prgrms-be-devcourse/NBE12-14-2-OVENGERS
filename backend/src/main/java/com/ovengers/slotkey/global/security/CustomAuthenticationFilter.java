package com.ovengers.slotkey.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class CustomAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    // 액세스 토큰 검사를 생략할 POST 요청
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && PUBLIC_AUTH_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Authorization 헤더 확인
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 토큰이 없으면 인증 정보를 등록하지 않고 다음으로 이동.
        // 접근 허용 여부는 SecurityConfig에서 판단한다.
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Bearer 형식 확인
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }

        String accessToken = authorization.substring(7).trim();

        if (accessToken.isEmpty()) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }

        // 3. JWT 서명과 만료 시각 검증
        // 검증에 실패하면 기존 JwtProvider가 null을 반환한다.
        Map<String, Object> payload =
                jwtProvider.payloadOrNull(accessToken);

        if (payload == null) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }

        // 4. 검증된 토큰에서 회원 ID 추출
        Long memberId = extractMemberId(payload);

        if (memberId == null) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }

        // 5. DB에서 현재 회원 정보 조회
        Member member = memberRepository.findById(memberId)
                .orElse(null);

        if (member == null) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }

        // 6. 정지·탈퇴 계정 차단
        if (member.getStatus() != MemberStatus.ACTIVE) {
            writeError(response, ErrorCode.ACCOUNT_INACTIVE);
            return;
        }

        // 7. 확인된 회원 정보를 AuthPrincipal에 담기
        AuthPrincipal principal = new AuthPrincipal(member);

        // 8. Spring Security에서 사용할 인증 객체 생성
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        // 9. 이번 요청의 인증 결과 등록
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 10. 다음 필터로 이동
        // 이후 Spring Security가 API 접근 권한을 검사한다.
        filterChain.doFilter(request, response);
    }

    // JWT의 id를 Long으로 변환한다.
    private Long extractMemberId(Map<String, Object> payload) {
        Object id = payload.get("id");

        if (id == null) {
            return null;
        }

        try {
            long memberId = Long.parseLong(id.toString());
            return memberId > 0 ? memberId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 필터에서 발생한 인증 실패를 공통 응답 형식으로 반환한다.
    private void writeError(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        SecurityContextHolder.clearContext();

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.fail(errorCode)
        );
    }
}