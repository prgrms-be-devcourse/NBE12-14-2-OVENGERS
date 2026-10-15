package com.ovengers.slotkey.outbox;

public class OutboxPublishException extends RuntimeException {
    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
