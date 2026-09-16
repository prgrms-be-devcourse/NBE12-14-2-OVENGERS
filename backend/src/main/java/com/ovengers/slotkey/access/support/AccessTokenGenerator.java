package com.ovengers.slotkey.access.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AccessTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    // 새로운 원문 출입 토큰 생성
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);

        // URL에서 사용할 수 있는 문자열로 변환
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
