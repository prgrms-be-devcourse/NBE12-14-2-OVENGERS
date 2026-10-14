package com.ovengers.slotkey.auth.controller;

import com.ovengers.slotkey.auth.dto.internal.VerificationCheckResult;
import com.ovengers.slotkey.auth.dto.request.VerificationCheckRequest;
import com.ovengers.slotkey.auth.dto.response.VerificationCheckResponse;
import com.ovengers.slotkey.auth.service.VerificationCheckService;
import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ovengers.slotkey.auth.dto.internal.ChallengeIssueResult;
import com.ovengers.slotkey.auth.dto.request.VerificationIssueRequest;
import com.ovengers.slotkey.auth.dto.response.VerificationIssueResponse;
import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import com.ovengers.slotkey.auth.service.VerificationChallengeService;
@RestController
@RequestMapping("/api/v1/auth/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationCheckService checkService;
    private final VerificationChallengeService challengeService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<VerificationCheckResponse>> check(
            @Valid @RequestBody VerificationCheckRequest request
    ) {
        VerificationCheckResult result = checkService.verify(
                request.email(),
                request.purpose(),
                request.challengeId(),
                request.code()
        );

        // 서비스의 트랜잭션 종료 후 실패를 HTTP 오류로 변환
        switch (result.status()) {
            case WRONG_CODE -> throw new BusinessException(
                    ErrorCode.VERIFICATION_CODE_MISMATCH
            );
            case INVALID_CHALLENGE -> throw new BusinessException(
                    ErrorCode.VERIFICATION_CHALLENGE_INVALID
            );
            case TEMPORARILY_BLOCKED -> throw new BusinessException(
                    ErrorCode.VERIFICATION_TEMPORARILY_BLOCKED
            );
            case ADMIN_LOCKED -> throw new BusinessException(
                    ErrorCode.VERIFICATION_ADMIN_LOCKED
            );
            case SUCCESS -> {
                return ResponseEntity.ok()
                        .cacheControl(
                                org.springframework.http.CacheControl.noStore()
                        )
                        .body(ApiResponse.success(
                                new VerificationCheckResponse(
                                        result.grant(),
                                        result.expiresAt()
                                )
                        ));
            }
        }

        throw new IllegalStateException("알 수 없는 인증 결과입니다.");
    }
    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<VerificationIssueResponse>> issue(
            @Valid @RequestBody VerificationIssueRequest request
    ) {
        ChallengeIssueResult result = challengeService.issue(
                request.email(),
                request.purpose()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        new VerificationIssueResponse(
                                result.challengeId(),
                                result.expiresAt(),
                                result.resendAvailableAt()
                        )
                )
        );
    }
}
