package com.ovengers.slotkey.auth.ratelimit;

/** Storage boundary for consuming one login-attempt slot in a fixed window. */
public interface LoginAttemptStore {

    /**
     * Atomically consumes a slot if the bucket is below the limit.
     * The email key uses the same database collation as member.email.
     */
    boolean tryAcquire(String email, long windowId, int limit);
}
