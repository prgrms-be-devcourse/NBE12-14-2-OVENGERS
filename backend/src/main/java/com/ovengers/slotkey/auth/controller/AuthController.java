package com.ovengers.slotkey.auth.controller;

import com.ovengers.slotkey.auth.dto.request.SignupRequest;
import com.ovengers.slotkey.auth.dto.response.SignupResponse;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

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
}