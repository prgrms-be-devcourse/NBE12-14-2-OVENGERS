/**
 * 예약 대기 화면용 순수 함수.
 * 서버 LocalDateTime 문자열은 타임존 변환 없이 잘라서 쓴다 — 서버가 보낸 벽시계 시각 그대로 보여 주기 위함이다.
 */

import type { DecoratedSlot } from '../types/api';
import { slotsInRange } from './slot';

/** ('2026-10-15', '22:30') → '2026-10-15T22:30:00' (서버 LocalDateTime 입력 형식) */
export function toLocalDateTime(date: string, time: string): string {
  return `${date}T${time}:00`;
}

/** '2026-10-15T22:30:00' → { date: '2026-10-15', time: '22:30' } */
export function splitLocalDateTime(value: string): { date: string; time: string } {
  return { date: value.slice(0, 10), time: value.slice(11, 16) };
}

/**
 * 대기 신청 모드에서는 지난 시간만 선택할 수 없다.
 * 비어 있는 칸도 구간에 포함할 수 있고, 점유 칸이 1개 이상인지는 아래 함수로 따로 확인한다
 * (서버 규칙: 구간에 점유 슬롯이 1개 이상일 때만 대기 신청 가능, WAITLIST_NOT_NEEDED).
 */
export function toWaitlistSlots(slots: DecoratedSlot[]): DecoratedSlot[] {
  return slots.map((slot) => ({ ...slot, selectable: !slot.past }));
}

/** 지난 시간이 아니면서 이미 예약된 칸 — 대기 신청의 대상이다. */
export function isReservedFutureSlot(slot: DecoratedSlot): boolean {
  return !slot.available && !slot.past;
}

/** 대기 신청 모드를 보여 줄지 판단한다: 예약된 미래 칸이 하나라도 있어야 한다. */
export function hasReservedFutureSlot(slots: DecoratedSlot[]): boolean {
  return slots.some(isReservedFutureSlot);
}

/** 선택 구간[startTime, endTime)에 예약된 칸이 1개 이상인지. 없으면 대기가 아니라 바로 예약해야 한다. */
export function rangeHasReservedSlot(
  slots: DecoratedSlot[],
  startTime: string | null,
  endTime: string | null,
): boolean {
  if (!startTime || !endTime) return false;
  const range = new Set(slotsInRange(startTime, endTime));
  return slots.some((slot) => range.has(slot.startTime) && isReservedFutureSlot(slot));
}
