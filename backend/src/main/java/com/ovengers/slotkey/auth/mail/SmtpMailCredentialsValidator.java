package com.ovengers.slotkey.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "prod"})
public class SmtpMailCredentialsValidator {

    public SmtpMailCredentialsValidator(
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password
    ) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD must be configured"
            );
        }
    }
}
