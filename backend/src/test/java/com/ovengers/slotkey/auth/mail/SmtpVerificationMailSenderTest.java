package com.ovengers.slotkey.auth.mail;

import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class SmtpVerificationMailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void 회원가입_메일의_발신자_수신자_제목과_본문을_구성한다() {
        SmtpVerificationMailSender sender = new SmtpVerificationMailSender(
                mailSender,
                "slotkey@example.com",
                "slotkey@example.com"
        );

        sender.sendCode(
                "member@example.com",
                VerificationPurpose.SIGNUP,
                "123456"
        );

        SimpleMailMessage message = capturedMessage();
        assertThat(message.getFrom()).isEqualTo("slotkey@example.com");
        assertThat(message.getTo()).containsExactly("member@example.com");
        assertThat(message.getSubject()).isEqualTo("[SlotKey] 회원가입 인증번호");
        assertThat(message.getText())
                .contains("회원가입 인증번호입니다.")
                .contains("인증번호: 123456")
                .contains("유효 시간: 발급 후 3분");
    }

    @Test
    void 비밀번호_재설정_메일의_제목과_본문을_구성한다() {
        SmtpVerificationMailSender sender = new SmtpVerificationMailSender(
                mailSender,
                "slotkey@example.com",
                "slotkey@example.com"
        );

        sender.sendCode(
                "member@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "654321"
        );

        SimpleMailMessage message = capturedMessage();
        assertThat(message.getSubject())
                .isEqualTo("[SlotKey] 비밀번호 재설정 인증번호");
        assertThat(message.getText())
                .contains("비밀번호 재설정 인증번호입니다.")
                .contains("인증번호: 654321");
    }

    @Test
    void 발신주소와_SMTP_로그인주소가_다르면_설정오류로_실패한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SmtpVerificationMailSender(
                        mailSender,
                        "different@example.com",
                        "slotkey@example.com"
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("VERIFICATION_MAIL_FROM must match SPRING_MAIL_USERNAME");
    }

    private SimpleMailMessage capturedMessage() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
