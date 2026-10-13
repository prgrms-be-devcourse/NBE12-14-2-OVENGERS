package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.LocalCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository
        extends JpaRepository<LocalCredential, Long> {
}