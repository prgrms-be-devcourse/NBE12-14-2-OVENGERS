# Slot Key 남은 테스트 4건 — 복붙용

> 컴파일·실행은 못 해봤습니다(Docker/Maven 접근 불가). 통합 테스트는 Docker Desktop을 켜고 `./gradlew test`(또는 팀이 쓰는 빌드 명령)로 돌려주세요.
> 기준은 지금 `dev`의 코드입니다(`ReservationIntegrationTestSupport`, `validateExtension`, Space 우선 잠금 순서 반영 상태).

## 먼저 알아야 할 것 — 4번은 "수정"이 아니라 "테스트만"

`dev`에는 이미 `ReservationTimePolicy.validateExtension`(30분 단위 / 같은 날짜 / 운영 종료 이내)이 있고 `ReservationExtendService`가 호출하며, 서비스 단위 테스트 3건(운영 종료 초과, 30분 미정렬, 날짜 다름)도 있습니다.
그래서 4번은 메인 코드를 고칠 일이 없고, **경계값 테스트만 `ReservationTimePolicyTest`에 추가**하면 됩니다(현재 `validateExtension` 직접 테스트가 0건).
앞에서 "연장 검증 누락"이라고 한 것은 제가 본 사본이 오래된 것이었기 때문입니다. 이슈 제목과 커밋 타입도 그에 맞게 바꿨습니다.

| # | 이슈 | 브랜치 | 커밋 |
|---|------|--------|------|
| 1 | [테스트] 연장과 취소가 동시에 들어와도 크레딧·슬롯 정합성 유지 검증 | `test/extend-cancel-race` | `test(reservation): verify extend and cancel racing leave credits and slots consistent` |
| 2 | [테스트] 시간대가 일부만 겹치는 동시 HOLD 시 1건만 성공하고 500이 나지 않는지 검증 | `test/slot-partial-overlap` | `test(reservation): verify partially overlapping concurrent holds yield one success and no server errors` |
| 3 | [테스트] 맞닿은 시간대(14:00~15:00, 15:00~16:00)의 동시 HOLD가 둘 다 성공하는지 검증 | `test/slot-adjacent-boundary` | `test(reservation): verify adjacent concurrent holds both succeed` |
| 4 | [테스트] 연장 종료 시각 경계값(운영 종료·30분 단위·날짜 넘김) 검증 보강 | `test/extend-time-boundaries` | `test(reservation): cover extend end time boundaries in ReservationTimePolicy` |

PR 설명(2~3줄)

1. 연장과 취소가 같은 예약에 동시에 처리될 때 어느 쪽이 먼저 커밋돼도 최종 상태가 같은지 검증합니다. 기대값은 CANCELLED, 슬롯 0개, 잔액 = 초기값 + 원 예약 총액입니다. 10판 반복하며, 취소가 연장 커밋 전의 총액으로 환불하면 실패합니다.
2. 14:00~15:00과 14:30~15:30처럼 일부만 겹치는 HOLD(및 포함 관계)가 동시에 들어와도 정확히 1건만 성공하고 나머지는 `RESERVATION_SLOT_CONFLICT`(409)임을 검증합니다. 데드락 등으로 500이 나면 실패합니다.
3. 끝 시각과 시작 시각이 정확히 맞닿는 두 HOLD가 동시에 들어와도 둘 다 성공하는지 검증합니다. 슬롯이 `[start, end)`로 저장되는지(off-by-one)를 DB 기준으로 확인합니다.
4. `validateExtension`의 경계값(운영 종료 정각 통과, +30분 거절, 30분·초 단위 미정렬, 날짜 넘김, 종료 시각이 늘지 않는 경우, null)을 단위 테스트로 고정합니다. 메인 코드 변경은 없습니다.

---

## 1. `ReservationExtendCancelRaceTest.java`

경로: `backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationExtendCancelRaceTest.java`

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 예약에 연장과 취소가 동시에 처리되는 경우를 실제 DB로 검증한다(core-domain-decisions 4-3, 6-2, 7).
 * 어느 쪽이 먼저 커밋되든 최종 결과는 하나로 수렴해야 한다.
 * - 연장이 먼저: 연장분(추가 슬롯·차감)이 반영된 뒤 취소되므로 환불액 = 원 예약 + 연장분 총액
 * - 취소가 먼저: 연장은 실패(상태가 CANCELLED)하고 환불액 = 원 예약 총액
 * 어느 쪽이든 잔액은 "초기값 + 원 예약 총액"이고 슬롯은 남지 않는다.
 * 겹치는 순간은 확률적이므로 판을 여러 번 반복한다.
 */
class ReservationExtendCancelRaceTest extends ReservationIntegrationTestSupport {

    private static final int INITIAL_BALANCE = 100_000;
    private static final int ORIGINAL_TOTAL = 10_000; // 14:00~15:00, 슬롯 2개 × 5,000원
    private static final int EXTENSION_COST = 10_000; // 15:00~16:00, 슬롯 2개 × 5,000원
    private static final int ROUNDS = 10;

    @Autowired
    private ReservationExtendService reservationExtendService;
    @Autowired
    private ReservationCancelService reservationCancelService;

    @Test
    @DisplayName("연장과 취소가 동시에 처리돼도 항상 CANCELLED로 끝나고, 슬롯은 남지 않으며, 환불은 실제 결제한 총액과 정확히 일치한다")
    void extendAndCancelConcurrently_alwaysEndCancelledWithMatchingRefund() {
        int extendedRounds = 0;

        for (int round = 0; round < ROUNDS; round++) {
            // 판마다 회원·공간을 새로 만들어 서로 영향을 주지 않게 한다.
            Long memberId = createMember(INITIAL_BALANCE);
            Long spaceId = createSpace();
            LocalDateTime endTime = tomorrowAt(15, 0);
            LocalDateTime newEndTime = tomorrowAt(16, 0);
            Long reservationId = createReservation(
                    memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), endTime);

            List<Runnable> tasks = List.of(
                    () -> reservationExtendService.extend(memberId, reservationId, endTime, newEndTime),
                    () -> reservationCancelService.cancel(memberId, reservationId));

            List<Throwable> results = runConcurrently(tasks);

            // 취소는 연장이 끝나든 말든 항상 성공해야 한다(연장은 상태를 바꾸지 않는다).
            assertThat(results.get(1)).as("round %d: 취소 결과", round).isNull();
            // 연장은 성공하거나, 취소가 먼저여서 연장 불가 / 상태 충돌로 끝나야 한다. 500이면 안 된다.
            assertThat(failures(results)).as("round %d: 연장 실패 사유", round)
                    .isSubsetOf(ErrorCode.RESERVATION_EXTEND_NOT_ALLOWED, ErrorCode.RESERVATION_STATE_CONFLICT);

            boolean extended = results.get(0) == null;
            if (extended) {
                extendedRounds++;
            }
            int expectedRefund = ORIGINAL_TOTAL + (extended ? EXTENSION_COST : 0);

            assertThat(statusOf(reservationId)).as("round %d", round).isEqualTo("CANCELLED");
            assertThat(countSlots(reservationId)).as("round %d: 남은 슬롯", round).isZero();
            assertThat(countLedger(reservationId, "REFUND")).as("round %d", round).isEqualTo(1);
            assertThat(countLedger(reservationId, "PENALTY")).as("round %d", round).isZero();
            assertThat(countLedger(reservationId, "RESERVATION_CHARGE")).as("round %d", round)
                    .isEqualTo(extended ? 1 : 0);
            // 핵심: 취소가 연장 커밋 전의 총액(10,000)으로 환불하면 여기서 실패한다.
            assertThat(sumLedger(reservationId, "REFUND")).as("round %d: 환불액 (extended=%s)", round, extended)
                    .isEqualTo(expectedRefund);
            // 잔액 = 초기값 - 연장 차감 + 환불 = 초기값 + 원 예약 총액 (연장 여부와 무관)
            assertThat(balanceOf(memberId)).as("round %d: 잔액", round)
                    .isEqualTo(INITIAL_BALANCE + ORIGINAL_TOTAL);
        }

        System.out.printf("[extend-cancel race] %d/%d rounds had extend commit first%n", extendedRounds, ROUNDS);
    }
}
```

**실패하면(환불액이 10,000으로 나오면)** 진짜 결함입니다. `ReservationCancelService.cancel`이 조건부 UPDATE **이전에** 읽어 둔 `reservation.getTotalAmount()`를 쓰기 때문에, 그 사이 연장이 커밋되면 연장분이 환불에서 빠집니다.
수정은 한 줄입니다. 조건부 UPDATE 이후에 최신 총액을 다시 읽으세요(UPDATE가 영속성 컨텍스트를 비우므로 `findById`는 DB에서 새로 읽습니다).

```java
// ReservationCancelService.cancel(...) 안
// 변경 전
int totalAmount = reservation.getTotalAmount();
// 변경 후
int totalAmount = reservationRepository.findById(reservationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND))
        .getTotalAmount();
```

이 수정을 넣는다면 커밋은 `fix(reservation): refund latest total amount when extend commits before cancel`, 브랜치는 `fix/cancel-refund-stale-total`로 분리하세요. `ReservationCancelServiceTest`는 `findById` 목이 같은 값을 반복 반환하면 그대로 통과합니다.

---

## 2. `ReservationPartialOverlapConcurrencyTest.java`

경로: `backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationPartialOverlapConcurrencyTest.java`

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시간대가 "일부만" 겹치는 HOLD 두 건이 동시에 들어오는 경우를 실제 DB로 검증한다(core-domain-decisions 4-1, 4-2).
 * 기존 ReservationConcurrencyTest는 "완전히 같은 시간대"만 다룬다. 여기서는 UNIQUE(space_id, slot_start)가
 * 겹치는 슬롯 하나만으로도 전체를 막아 주는지, 그리고 진 요청이 데드락 등으로 500이 되지 않는지를 확인한다.
 * 진 요청의 예약·슬롯은 롤백되어 흔적이 남지 않아야 한다.
 */
class ReservationPartialOverlapConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int ROUNDS = 10;

    @Autowired
    private ReservationHoldService reservationHoldService;

    @Test
    @DisplayName("14:00~15:00과 14:30~15:30(뒤 30분만 겹침)이 동시에 들어오면 정확히 1건만 성공한다")
    void createHold_partialOverlap_onlyOneSucceeds() {
        for (int round = 0; round < ROUNDS; round++) {
            assertOnlyOneWins(round, tomorrowAt(14, 0), tomorrowAt(15, 0), tomorrowAt(14, 30), tomorrowAt(15, 30));
        }
    }

    @Test
    @DisplayName("14:00~16:00 안에 14:30~15:00이 포함되는 요청이 동시에 들어오면 정확히 1건만 성공한다")
    void createHold_containedRange_onlyOneSucceeds() {
        for (int round = 0; round < ROUNDS; round++) {
            assertOnlyOneWins(round, tomorrowAt(14, 0), tomorrowAt(16, 0), tomorrowAt(14, 30), tomorrowAt(15, 0));
        }
    }

    private void assertOnlyOneWins(int round, LocalDateTime startA, LocalDateTime endA,
                                   LocalDateTime startB, LocalDateTime endB) {
        // 판마다 공간·회원을 새로 만들어 이전 판의 슬롯과 섞이지 않게 한다.
        Long spaceId = createSpace();
        Long memberA = createMember(0);
        Long memberB = createMember(0);

        List<Throwable> results = runConcurrently(List.of(
                () -> reservationHoldService.createHold(memberA, spaceId, startA, endA),
                () -> reservationHoldService.createHold(memberB, spaceId, startB, endB)));

        assertThat(successCount(results)).as("round %d: 성공 수", round).isEqualTo(1);
        // 데드락/락 대기 초과 등 DB 예외가 그대로 새면 ErrorCode가 아니라 예외 자체가 담겨 여기서 실패한다.
        assertThat(failures(results)).as("round %d: 실패 사유", round)
                .containsExactly(ErrorCode.RESERVATION_SLOT_CONFLICT);

        // 진 요청의 예약 행은 롤백되어 남지 않는다.
        List<Long> reservationIds = jdbcTemplate.queryForList(
                "SELECT id FROM reservation WHERE space_id = ?", Long.class, spaceId);
        assertThat(reservationIds).as("round %d: 남은 예약", round).hasSize(1);

        // 슬롯은 이긴 예약 몫만 남고, 전부 그 예약에 묶여 있다.
        Long winnerId = reservationIds.get(0);
        int expectedSlots = winnerSlotCount(winnerId, spaceId);
        assertThat(countSlots(winnerId)).as("round %d: 이긴 예약의 슬롯", round).isEqualTo(expectedSlots);
        assertThat(countSlotsInRange(spaceId, tomorrowAt(0, 0), tomorrowAt(23, 30).plusMinutes(30)))
                .as("round %d: 공간 전체 슬롯", round).isEqualTo(expectedSlots);
    }

    /** 이긴 예약의 (end - start)를 30분 슬롯 수로 환산한다. */
    private int winnerSlotCount(Long reservationId, Long spaceId) {
        Integer minutes = jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(MINUTE, start_time, end_time) FROM reservation WHERE id = ? AND space_id = ?",
                Integer.class, reservationId, spaceId);
        return minutes / 30;
    }
}
```

**실패 유형별 대응**
- `failures`에 `DeadlockLoserDataAccessException`·`CannotAcquireLockException` 같은 예외가 찍히면, 두 트랜잭션이 슬롯을 다른 순서로 잡아 데드락이 난 것입니다. `ReservationSlotService.secureSlots`의 `catch`를 아래처럼 넓혀 409로 매핑하세요(커밋 `fix(reservation): map slot lock failures to conflict`).
  ```java
  } catch (DataIntegrityViolationException | org.springframework.dao.PessimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.RESERVATION_SLOT_CONFLICT);
  }
  ```
- 슬롯 삽입은 `buildSlotStarts` 순서(오름차순)라 데드락이 나지 않을 것으로 예상합니다. 통과하면 그 자체가 "오름차순 삽입이 지켜진다"는 회귀 방지가 됩니다.

---

## 3. `ReservationAdjacentSlotConcurrencyTest.java`

경로: `backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationAdjacentSlotConcurrencyTest.java`

```java
package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 끝 시각과 시작 시각이 정확히 맞닿는 두 HOLD(14:00~15:00, 15:00~16:00)가 동시에 들어오는 경우를 검증한다.
 * 슬롯은 [start, end) 반열린 구간으로 저장되므로 15:00 슬롯은 두 번째 예약의 것이고, 둘은 충돌하지 않아야 한다.
 * off-by-one으로 첫 예약이 15:00 슬롯까지 잡거나(불필요한 충돌), 슬롯이 하나 모자라면 여기서 실패한다.
 */
class ReservationAdjacentSlotConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int ROUNDS = 10;

    @Autowired
    private ReservationHoldService reservationHoldService;

    @Test
    @DisplayName("맞닿은 시간대 두 HOLD가 동시에 들어와도 둘 다 성공하고, 각자 자기 슬롯 2개만 가진다")
    void createHold_adjacentRanges_bothSucceed() {
        for (int round = 0; round < ROUNDS; round++) {
            Long spaceId = createSpace();
            Long memberA = createMember(0);
            Long memberB = createMember(0);

            List<Throwable> results = runConcurrently(List.of(
                    () -> reservationHoldService.createHold(memberA, spaceId, tomorrowAt(14, 0), tomorrowAt(15, 0)),
                    () -> reservationHoldService.createHold(memberB, spaceId, tomorrowAt(15, 0), tomorrowAt(16, 0))));

            assertThat(failures(results)).as("round %d: 실패", round).isEmpty();
            assertThat(successCount(results)).as("round %d: 성공 수", round).isEqualTo(2);

            List<Long> reservationIds = jdbcTemplate.queryForList(
                    "SELECT id FROM reservation WHERE space_id = ?", Long.class, spaceId);
            assertThat(reservationIds).as("round %d: 예약 수", round).hasSize(2);
            reservationIds.forEach(id ->
                    assertThat(countSlots(id)).as("round %d: 예약 %d의 슬롯", round, id).isEqualTo(2));

            // 14:00, 14:30, 15:00, 15:30 — 정확히 4개, 경계 밖(13:30 이전 / 16:00 이후)에는 없다.
            assertThat(countSlotsInRange(spaceId, tomorrowAt(14, 0), tomorrowAt(16, 0))).isEqualTo(4);
            assertThat(countSlotsInRange(spaceId, tomorrowAt(9, 0), tomorrowAt(14, 0))).isZero();
            assertThat(countSlotsInRange(spaceId, tomorrowAt(16, 0), tomorrowAt(22, 0))).isZero();
        }
    }
}
```

---

## 4. `ReservationTimePolicyTest.java`에 추가 (파일 맨 끝 `}` 바로 앞에 붙여넣기)

경로: `backend/src/test/java/com/ovengers/slotkey/reservation/policy/ReservationTimePolicyTest.java`
기존 import(`BusinessException`, `ErrorCode`, `DisplayName`, `Test`, `LocalDateTime`, `LocalTime`, `assertThatCode`, `assertThatThrownBy`)와 상수 `CLOSING`(22:00)을 그대로 씁니다. import 추가는 없습니다.

```java

    // ---------- validateExtension ----------

    private static final LocalDateTime EXTEND_FROM = LocalDateTime.of(2026, 9, 18, 21, 0);

    @Test
    @DisplayName("연장 종료 시각이 운영 종료 시각과 정확히 같으면 통과한다")
    void validateExtension_untilExactlyClosing_doesNotThrow() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 22, 0);

        assertThatCode(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연장 종료 시각이 운영 종료를 한 슬롯(30분)이라도 넘으면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_oneSlotPastClosing_throwsInvalidTime() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 22, 30);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장 종료 시각이 30분 단위가 아니면(분이 어긋나거나 초가 있으면) INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_unalignedEndTime_throwsInvalidTime() {
        LocalDateTime unalignedMinute = LocalDateTime.of(2026, 9, 18, 21, 15);
        LocalDateTime withSeconds = LocalDateTime.of(2026, 9, 18, 21, 30, 10);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, unalignedMinute, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, withSeconds, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장으로 날짜가 넘어가면(자정 포함) INVALID_RESERVATION_TIME 예외가 발생한다")
    void validateExtension_crossesDate_throwsInvalidTime() {
        LocalDateTime midnight = LocalDateTime.of(2026, 9, 19, 0, 0);
        LocalDateTime nextDay = LocalDateTime.of(2026, 9, 19, 10, 0);

        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, midnight, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, nextDay, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("연장 종료 시각이 기존 종료 시각과 같거나 이르면 VALIDATION_FAILED 예외가 발생한다")
    void validateExtension_notAfterCurrentEnd_throwsValidationFailed() {
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, EXTEND_FROM, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(
                EXTEND_FROM, EXTEND_FROM.minusMinutes(30), CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("연장 시각 정보가 null이면 VALIDATION_FAILED 예외가 발생한다")
    void validateExtension_nullTime_throwsValidationFailed() {
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(null, EXTEND_FROM, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, null, CLOSING))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("운영 시간 안에서 30분 단위로 같은 날 연장하면 예외 없이 통과한다")
    void validateExtension_validExtension_doesNotThrow() {
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 18, 21, 30);

        assertThatCode(() -> ReservationTimePolicy.validateExtension(EXTEND_FROM, newEnd, CLOSING))
                .doesNotThrowAnyException();
    }
```

---

## 5. docs에 추가할 내용

### `docs/core-domain-decisions.md`

**§7 연장 (현재 `### 7-2. 처리`의 마지막 불릿 "실패해도 원 예약은 무손상이다…" 바로 아래, `---` 앞)** — 아래 불릿을 추가하세요.

```markdown
- **연장 종료 시각 규칙**: 신규 예약과 같은 시간 규칙을 따른다 — ① 30분 단위(분·초 모두), ② 기존 예약과 같은 날짜 안(자정을 넘기는 연장 불가), ③ 공간 운영 종료 시각 이내(정각까지는 허용, +30분은 거절). 위반은 `INVALID_RESERVATION_TIME`(400). 새 종료 시각이 기존보다 늦지 않으면 `VALIDATION_FAILED`(400).
```

**§12 테스트 목록**

- `### 동시성` 목록 맨 끝(도어 토큰 발급 N건 동시 항목 다음)에 추가:
  ```markdown
  - 연장 ↔ 본인 취소 동시 → 취소는 항상 성공, 최종 CANCELLED·슬롯 0개, 환불액 = 실제 결제 총액(연장이 먼저면 연장분 포함), 잔액 = 초기값 + 원 예약 총액
  - 시간대가 일부만 겹치는 HOLD 동시(부분 겹침·포함) → 정확히 1건 성공, 나머지 409(500 아님), 진 요청의 예약·슬롯 잔존 없음
  - 맞닿은 시간대 HOLD 동시(14:00~15:00, 15:00~16:00) → 둘 다 성공, 슬롯은 각자 2개
  ```
- `### 시간 경계` 목록 맨 끝(`Clock`을 주입해… 문장 앞)에 추가:
  ```markdown
  연장 종료 시각: `closing` 정각(통과) / `closing+30m`(거절) / 30분 미정렬·초 단위 값(거절) / 자정 넘김(거절) / 기존 종료 시각과 같거나 이른 값(거절)
  ```

### `docs/api-spec.md`

**§5-5 연장의 `오류:` 줄(약 139행)** — 실제 코드와 맞게 교체하세요.

```markdown
오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), INVALID_RESERVATION_TIME(400, 30분 단위 아님·날짜 넘김·운영 종료 초과), VALIDATION_FAILED(400, 새 종료 시각이 기존보다 늦지 않음), RESERVATION_SLOT_CONFLICT(409, 연장 슬롯 일부/전부 점유 — 응답에 가능한 최대 종료 시각 힌트 포함), RESERVATION_STATE_CONFLICT(409, `expectedEndTime` 불일치·동시 요청 경합), RESERVATION_EXTEND_NOT_ALLOWED(422, 연장 불가 상태·이미 종료된 예약), INSUFFICIENT_BALANCE(422)
```

### 이번 작업과 별개로 문서에서 발견한 불일치 (선택)

1. `api-spec.md` 30행 `422 | 정책 위반`에 "운영시간 외, 30분 배수 위반"이 적혀 있는데, 실제 `INVALID_RESERVATION_TIME`은 **400**입니다. 표에서 해당 두 항목을 400 행으로 옮기거나 코드를 422로 바꾸는 쪽으로 정해야 합니다(프런트가 상태 코드를 보고 분기한다면 지금 정해두는 편이 낫습니다).
2. `core-domain-decisions.md` 549행 `- - 결제 실패…`는 불릿이 겹쳐 있습니다(`- `를 하나 지우면 됩니다).
3. §7-2와 `api-spec` §5-5는 "상태 이력 저장"이 연장 흐름에 있다고 하지만, `ReservationExtendService`는 이력을 저장하지 않습니다. 코드를 맞출지 문서를 맞출지 결정이 필요합니다(이번 4건과 무관).
