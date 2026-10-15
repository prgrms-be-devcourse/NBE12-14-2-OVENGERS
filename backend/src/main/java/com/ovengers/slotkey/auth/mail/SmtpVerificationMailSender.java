package com.ovengers.slotkey.auth.mail;

import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationMailSender
        implements VerificationMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationMailSender(
            JavaMailSender mailSender,
            @Value("${app.verification.mail-from}") String from,
            @Value("${spring.mail.username}") String smtpUsername
    ) {
        if (!from.equals(smtpUsername)) {
            throw new IllegalStateException(
                    "VERIFICATION_MAIL_FROM must match SPRING_MAIL_USERNAME"
            );
        }
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendCode(
            String email,
            VerificationPurpose purpose,
            String rawCode
    ) {
        String action = switch (purpose) {
            case SIGNUP -> "회원가입";
            case PASSWORD_RESET -> "비밀번호 재설정";
        };

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[SlotKey] " + action + " 인증번호");
        message.setText("""
                SlotKey %s 인증번호입니다.

                인증번호: %s
                유효 시간: 발급 후 3분

                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(action, rawCode));

        mailSender.send(message);
    }
}
