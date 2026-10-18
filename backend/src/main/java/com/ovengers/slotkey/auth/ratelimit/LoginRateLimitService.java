package com.ovengers.slotkey.auth.ratelimit;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    static final int MAX_ATTEMPTS = 10;
    static final long WINDOW_SECONDS = 15 * 60;

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);

    private final LoginAttemptStore loginAttemptStore;
    private final Clock clock;

    public AttemptDecision acquire(String email) {
        Instant now = clock.instant();
        long windowId = Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS);
        boolean allowed;
        try {
            allowed = loginAttemptStore.tryAcquire(email, windowId, MAX_ATTEMPTS);
        } catch (DataAccessException | TransactionException exception) {
            log.warn("Login rate-limit storage unavailable ({})",
                    exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE);
        }

        if (allowed) {
            return new AttemptDecision(true, 0);
        }

        Instant nextWindow = Instant.ofEpochSecond((windowId + 1) * WINDOW_SECONDS);
        Duration remaining = Duration.between(now, nextWindow);
        long retryAfterSeconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            retryAfterSeconds++;
        }
        return new AttemptDecision(false, retryAfterSeconds);
    }

    public record AttemptDecision(boolean allowed, long retryAfterSeconds) {
    }
}
