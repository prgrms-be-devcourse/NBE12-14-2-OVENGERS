/**
 * 30분 슬롯 계산.
 * 예약의 최소 단위는 30분이며, 시작·종료 시각은 30분의 배수여야 합니다.
 * (docs/requirements.md FR-RESV-02)
 */

import { toDate, toTimeString } from './date';

export const SLOT_MINUTES = 30;

/** '14:00' → 840 (분) */
export function toMinutes(timeString) {
  const [h, m] = timeString.split(':').map(Number);
  return h * 60 + m;
}

/** 840 → '14:00' */
export function toTime(minutes) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function isAlignedToSlot(timeString) {
  return toMinutes(timeString) % SLOT_MINUTES === 0;
}

/**
 * 운영시간 전체를 30분 슬롯 배열로 만듭니다.
 * 마지막 슬롯의 종료 시각이 closingTime 을 넘지 않습니다.
 */
export function buildDaySlots(openingTime, closingTime) {
  const start = toMinutes(openingTime);
  const end = toMinutes(closingTime);
  const slots = [];
  for (let m = start; m + SLOT_MINUTES <= end; m += SLOT_MINUTES) {
    slots.push({ startTime: toTime(m), endTime: toTime(m + SLOT_MINUTES) });
  }
  return slots;
}

/** 시작~종료 사이에 포함되는 슬롯 시작 시각 목록 */
export function slotsInRange(startTime, endTime) {
  const result = [];
  for (let m = toMinutes(startTime); m < toMinutes(endTime); m += SLOT_MINUTES) {
    result.push(toTime(m));
  }
  return result;
}

export function countSlots(startTime, endTime) {
  return (toMinutes(endTime) - toMinutes(startTime)) / SLOT_MINUTES;
}

/** 슬롯이 이미 지난 시각인지. 서버도 같은 검사를 하지만 화면에서 미리 막습니다. */
export function isPastSlot(dateString, slotStartTime, now = new Date()) {
  return toDate(dateString, slotStartTime).getTime() <= now.getTime();
}

/**
 * 서버의 슬롯 가용성 응답과 화면 상태를 합쳐 슬롯별 선택 가능 여부를 만듭니다.
 * availability: [{ startTime, available }]
 */
export function decorateSlots(availability, dateString, now = new Date()) {
  return availability.map((slot) => {
    const past = isPastSlot(dateString, slot.startTime, now);
    return {
      ...slot,
      past,
      selectable: slot.available && !past,
      reason: past ? '지난 시간' : slot.available ? null : '예약됨',
    };
  });
}

/** 선택한 두 슬롯 사이가 모두 선택 가능한지 (중간에 점유 슬롯이 끼면 false) */
export function isContinuousRange(slots, startTime, endTime) {
  const range = slotsInRange(startTime, endTime);
  return range.every((t) => slots.find((s) => s.startTime === t)?.selectable);
}

/** 예약 종료 시각 = 마지막 선택 슬롯의 시작 + 30분 */
export function endTimeOf(lastSlotStartTime) {
  return toTime(toMinutes(lastSlotStartTime) + SLOT_MINUTES);
}

/** Date → 슬롯 시작 시각 문자열 (내림) */
export function floorToSlot(date) {
  const d = new Date(date);
  d.setMinutes(Math.floor(d.getMinutes() / SLOT_MINUTES) * SLOT_MINUTES, 0, 0);
  return toTimeString(d);
}
