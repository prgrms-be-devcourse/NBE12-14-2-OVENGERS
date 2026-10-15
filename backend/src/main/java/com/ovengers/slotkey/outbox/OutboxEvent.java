package com.ovengers.slotkey.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, length = 36, unique = true)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    @Column(nullable = false, length = 100)
    private String topic;
    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;
    @Column(nullable = false, columnDefinition = "json")
    private String payload;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String eventId, Long spaceId, String payload, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.eventType = "SlotReleased";
        this.topic = "slot-released";
        this.partitionKey = spaceId.toString();
        this.payload = payload;
        this.status = "PENDING";
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public String getPayload() { return payload; }
}
