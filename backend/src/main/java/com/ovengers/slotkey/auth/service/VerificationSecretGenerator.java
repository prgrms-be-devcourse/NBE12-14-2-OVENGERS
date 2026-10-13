package com.ovengers.slotkey.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Component
public class VerificationSecretGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        return String.format(
                Locale.ROOT,
                "%08d",
                secureRandom.nextInt(100_000_000)
        );
    }

    public String generateGrant() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}