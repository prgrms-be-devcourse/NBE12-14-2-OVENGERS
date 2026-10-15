package com.ovengers.slotkey.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SlotReleasedRecorder {
    public enum Reason { CANCELLED, FORCE_CANCELLED, HOLD_EXPIRED }

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SlotReleasedRecorder(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long reservationId, Long spaceId, Reason reason) {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new Payload(eventId, reservationId, reason, now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize SlotReleased event", e);
        }
        // Flush earlier pending writes before the IDENTITY insert acquires the outbox lock.
        repository.flush();
        repository.save(new OutboxEvent(eventId, spaceId, payload, now));
    }

    private record Payload(String eventId, Long reservationId, Reason reason, LocalDateTime occurredAt) {}
}
