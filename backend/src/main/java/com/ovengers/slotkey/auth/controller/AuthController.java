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
import org.springframework.beans.factory.annotation.Value;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급 및 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    @Value("${app.auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site}")
    private String cookieSameSite;

    // 공통 ApiResponse 형식이 정해지면 추후 반환 형태 수정
    @Operation(
            summary = "회원가입",
            description = """
                이메일, 비밀번호, 비밀번호 확인, 닉네임으로 가입합니다.
                비밀번호와 확인값이 일치해야 하며, 이미 사용 중인 이메일은 가입할 수 없습니다.
                가입 성공 시 회원 정보를 반환합니다. 로그인은 별도로 진행해야 합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 입력값 누락, 입력 형식 오류 또는 비밀번호 확인 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 검증 실패",
                                            value = """
                                                {
                                                  "status": "FAIL",
                                                  "code": "VALIDATION_FAILED",
                                                  "message": "비밀번호 확인을 입력해주세요."
                                                }
                                                """
                                    ),
                                    @ExampleObject(
                                            name = "비밀번호 확인 불일치",
                                            value = """
                                                {
                                                  "status": "FAIL",
                                                  "code": "PASSWORD_CONFIRM_MISMATCH",
                                                  "message": "비밀번호가 일치하지 않습니다."
                                                }
                                                """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 이메일",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                        {
                                          "status": "FAIL",
                                          "code": "EMAIL_ALREADY_EXISTS",
                                          "message": "이미 사용 중인 이메일입니다."
                                        }
                                        """
                            )
                    )
            )
    })
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
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
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
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();

        // 3. 쿠키 삭제 헤더와 본문 없는 성공 응답
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}