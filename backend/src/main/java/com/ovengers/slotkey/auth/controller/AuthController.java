package com.ovengers.slotkey.auth.controller;

import com.ovengers.slotkey.auth.dto.request.LoginRequest;
import com.ovengers.slotkey.auth.dto.request.SignupRequest;
import com.ovengers.slotkey.auth.dto.response.LoginResponse;
import com.ovengers.slotkey.auth.dto.response.SignupResponse;
import com.ovengers.slotkey.auth.service.AuthService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ovengers.slotkey.auth.dto.internal.LoginResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.ovengers.slotkey.global.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    // 공통 ApiResponse 형식이 정해지면 추후 반환 형태 수정
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request
    ) {
        Member member = memberService.signup(
                request.email(),
                request.password(),
                request.passwordConfirm(),
                request.nickname()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(new SignupResponse(member)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request
    ){
        // 1. 로그인 처리 후 액세스·리프레시 토큰을 받음
        LoginResult result = authService.login(
                request.email(),
                request.password()
        );

        // 2. 리프레시 토큰을 담은 쿠키 구성
        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발용. HTTPS 배포에서는 true로 변경
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(60L * 60 * 24) // 1일, 초 단위
                .build();

        // 3. 리프레시는 쿠키로, 액세스는 응답 본문으로 전달
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(
                        new LoginResponse(result.accessToken())
                ));
    }


    // 엑세스 토큰 갱신 api
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false)
            String rawRefreshToken
    ) {
        String accessToken = authService.refresh(rawRefreshToken);

        return ResponseEntity.ok()
                .body(ApiResponse.success(
                        new LoginResponse(accessToken)
                ));
    }

    // 로그아웃 메서드
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false)
            String rawRefreshToken
    ) {
        // 1. DB의 리프레시 토큰 폐기
        authService.logout(rawRefreshToken);

        // 2. 기존 쿠키를 삭제하기 위한 쿠키 설정
        ResponseCookie deleteCookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 로컬 HTTP용. HTTPS 배포에서는 true
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        // 3. 쿠키 삭제 헤더와 본문 없는 성공 응답
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}