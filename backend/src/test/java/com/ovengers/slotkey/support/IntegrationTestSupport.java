package com.ovengers.slotkey.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.auth.service.AuthSessionService;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.entity.Member;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
public abstract class IntegrationTestSupport {

    @Autowired
    protected AuthSessionService authSessionService;

    protected String issueSessionBoundAccessToken(Member member, JwtProvider jwtProvider) {
        AuthSession session = authSessionService.create(member);
        return jwtProvider.genAccessToken(
                member,
                session.getId(),
                session.getExpiresAt()
        );
    }
}
