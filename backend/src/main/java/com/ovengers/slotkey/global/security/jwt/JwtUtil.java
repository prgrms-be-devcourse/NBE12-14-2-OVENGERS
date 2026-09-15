package com.ovengers.slotkey.global.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {
    public static String createToken(String secretKey, long expireMillis, Map<String, Object> body) {

        //문자열을 연산에 사용할 수 있게 바이트 배열로 변환, 그리고 그 바이트 배열을 HMAC 서명용 객체로 만든다.
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expireMillis);

        return Jwts.builder() //JWT로 만들기 위한 빌더를 준비
                .claims(body)  //전달받은 회원 정보를 Payload에 넣음
                .issuedAt(issuedAt)  // 발급시간
                .expiration(expiration) // 만료시간 추가
                .signWith(key) // 이 키로 서명 하도록 설정
                .compact(); // 최종 문자열 만들기
    }

    public static Map<String, Object> payloadOrNull(
            String token,
            String secretKey
    ) {
        SecretKey key = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );

        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}