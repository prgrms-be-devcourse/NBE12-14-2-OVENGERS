package com.ovengers.slotkey.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class VerificationSecretHasher {

    private final SecretKeySpec key;

    public VerificationSecretHasher(
            @Value("${app.verification.hash-secret}") String secret
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "인증 검증용 비밀키는 최소 32바이트여야 합니다."
            );
        }

        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String hashCode(Long guardId, String rawCode) {
        return hash("CODE:" + guardId + ":" + rawCode);
    }

    public boolean matchesCode(
            Long guardId,
            String rawCode,
            String storedHash
    ) {
        byte[] expected = HexFormat.of().parseHex(storedHash);
        byte[] actual = HexFormat.of().parseHex(
                hashCode(guardId, rawCode)
        );

        return MessageDigest.isEqual(expected, actual);
    }

    public String hashGrant(String rawGrant) {
        return hash("GRANT:" + rawGrant);
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);

            byte[] result = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "인증 검증값 생성에 실패했습니다.",
                    e
            );
        }
    }
}