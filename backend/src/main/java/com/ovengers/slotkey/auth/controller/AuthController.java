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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    // 공통 ApiResponse 형식이 정해지면 추후 반환 형태 수정
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        Member member = memberService.signup(
                request.email(),
                request.password(),
                request.passwordConfirm(),
                request.nickname()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new SignupResponse(member));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody @Valid LoginRequest request
    ) {
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
                .maxAge(60L * 60 * 24 * 7) // 7일, 초 단위
                .build();

        // 3. 리프레시는 쿠키로, 액세스는 응답 본문으로 전달
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(result.accessToken()));
    }
}