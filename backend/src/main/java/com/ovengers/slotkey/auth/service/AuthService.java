package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ovengers.slotkey.auth.dto.internal.LoginResult;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.auth.entity.LocalCredential;
import com.ovengers.slotkey.auth.repository.LocalCredentialRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final LocalCredentialRepository localCredentialRepository;

    // 리프레시 토큰을 DB에 저장하므로 readOnly 제거
    @Transactional
    public LoginResult login(String email, String password) {
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmailForUpdate(email)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_CREDENTIALS)
                );

        // 회원 ID로 로컬 인증 정보 조회
        LocalCredential credential = localCredentialRepository
                .findById(member.getId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_CREDENTIALS)
                );
        // 비밀번호 검증
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // ACTIVE 회원만 로그인 허용
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        // 액세스 토큰 생성
        // 리프레시 토큰 생성 및 DB 저장
        var issuedRefreshToken = refreshTokenService.issueRefreshToken(member);
        String accessToken = jwtProvider.genAccessToken(
                member,
                issuedRefreshToken.sessionId(),
                issuedRefreshToken.sessionExpiresAt()
        );

        // 두 토큰을 컨트롤러에 전달
        return new LoginResult(accessToken, issuedRefreshToken.rawToken());
    }

    //리프레시 토큰을 이용해 엑세스 토큰을 갱신하기
    @Transactional
    public String refresh(String rawRefreshToken) {
        // 리프레시가 유효한지 확인하고 회원을 받음
        var validation = refreshTokenService
                .validateRefreshToken(rawRefreshToken);

        // 검증된 회원의 새 액세스 토큰 발급
        return jwtProvider.genAccessToken(
                validation.member(),
                validation.authSession().getId(),
                validation.authSession().getExpiresAt()
        );
    }
    
    //로그아웃 메서드, 리프레시 토큰에 값을 넣어 로그아웃
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
    }
}
