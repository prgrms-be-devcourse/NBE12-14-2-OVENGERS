package com.ovengers.slotkey.auth.mail;

import com.ovengers.slotkey.auth.entity.VerificationPurpose;

public interface VerificationMailSender {

    void sendCode(
            String email,
            VerificationPurpose purpose,
            String rawCode
    );
}