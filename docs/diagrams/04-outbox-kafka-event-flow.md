# Outbox + Kafka 이벤트 흐름 — 변경과 이벤트를 한 트랜잭션에, 효과는 한 번만

> **한 줄 메시지**: 슬롯이 풀리면 같은 트랜잭션에서 `outbox_event`에 기록하고, relay가 Kafka로 내보내며, 컨슈머가 중복을 걸러 알림을 저장한다. 전달은 최소 1회, 효과는 한 번이다.

## 정상 흐름

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자
    participant R as 취소·만료 서비스
    participant DB as MySQL
    participant RL as OutboxRelay (@Scheduled)
    participant K as Kafka<br/>slot-released
    participant CS as 컨슈머<br/>slotkey-waitlist-notifier

    U->>R: 예약 취소 (또는 강제 취소·홀드 만료)
    rect rgb(235, 245, 255)
        Note over R,DB: 하나의 트랜잭션
        R->>DB: 상태 전이 + 슬롯 삭제 + 환불
        R->>DB: outbox_event INSERT (PENDING)
        R->>DB: COMMIT
    end
    R-->>U: 취소 성공 (Kafka 상태와 무관)

    loop 주기적 폴링
        RL->>DB: PENDING 조회 (id 순, 잠금 없음)
        RL->>K: send (key = spaceId)
        K-->>RL: ack
        RL->>DB: PUBLISHED 로 조건부 UPDATE
    end

    K->>CS: SlotReleased 이벤트
    rect rgb(240, 250, 240)
        Note over CS,DB: 컨슈머 트랜잭션
        CS->>DB: INSERT IGNORE processed_event
        alt 영향 행 1 (처음 보는 이벤트)
            CS->>DB: 예약 구간 조회, 대기자 선별, 알림 INSERT
        else 영향 행 0 (이미 처리함)
            CS->>CS: 아무것도 하지 않음
        end
    end
```

## 실패와 중복이 생기는 지점

```mermaid
flowchart LR
    A["취소 트랜잭션 롤백"] -->|"outbox 행도 함께 사라짐"| A1["이벤트 없음 (정상)"]
    B["Kafka 장애"] -->|"취소는 이미 성공<br/>행은 PENDING, attempts만 증가"| B1["복구 후 이어서 발행"]
    C["전송 성공 후<br/>PUBLISHED 갱신 직전 장애"] -->|"다음 라운드에 재전송"| C1["같은 eventId가 두 번 전달"]
    C1 --> D["컨슈머<br/>processed_event UNIQUE로 흡수"]
    E["컨슈머 처리 실패"] -->|"1초 간격 2회 재시도 후 건너뜀"| E1["DLT 없음 (3차 과제)"]
```

## 왜 이렇게 했는가

- **Kafka로 직접 발행하지 않았다.** DB 커밋과 발행은 원자적이지 않아서, 커밋 뒤 발행이 실패하면 유실되고 트랜잭션 안에서 발행하면 롤백된 변경의 이벤트가 나간다(이중 쓰기).
- **전달은 최소 1회, 중복은 컨슈머가 흡수한다.** 정확히 한 번 전달을 만들려 하지 않고, `UNIQUE(consumer, event_id)`와 같은 트랜잭션의 `INSERT IGNORE`로 효과를 한 번으로 만든다.
- **`outbox_event`에는 FK를 걸지 않고, relay는 잠금 없이 폴링한다.** 취소·만료 트랜잭션과 락 대기가 생기면 이전에 겪은 Member/Space 데드락 경로가 다시 열리기 때문이다.
- **Kafka는 기본 꺼짐(`slotkey.kafka.enabled=false`)이다.** 브로커가 없는 팀 EC2 배포가 기동에 실패하지 않게 하려는 것이다.

## 한계

- 인스턴스 2개 이상이면 relay가 같은 행을 중복 발행한다(1개 가정).
- 계속 실패하는 행이 쌓이면 뒤의 새 이벤트가 막힌다(head-of-line). `FAILED` 상태·DLT·백오프는 3차 과제다.
- 건너뛴 이벤트는 로그에만 남고 복구 수단이 없다.

## 근거

| 내용 | 위치 |
| --- | --- |
| 기록(트랜잭션 필수) | `backend/src/main/java/com/ovengers/slotkey/outbox/SlotReleasedRecorder.java` |
| 폴링·발행 | `backend/src/main/java/com/ovengers/slotkey/outbox/OutboxRelay.java`, `KafkaOutboxPublisher.java` |
| 테이블 | `V18__create_outbox_event_table.sql`, `V19__create_processed_event_table.sql` |
| 컨슈머 설정 | `backend/src/main/java/com/ovengers/slotkey/notification/consumer/KafkaConsumerConfig.java` |
| 결정·검증 기록 | [`docs/decisions/outbox-kafka.md`](../decisions/outbox-kafka.md) (발행 지점 3곳, 타임아웃 불변식, 로컬 Kafka E2E 결과 포함) |

> 이 그림의 흐름과 한계는 위 결정 문서를 근거로 했다. `OutboxRelay`, `SlotReleasedRecorder`, `KafkaConsumerConfig` 소스는 직접 열어 보지 않았고, 컨슈머 리스너·핸들러 클래스 이름도 확인하지 못해 그림에서 "컨슈머"로만 표기했다.
