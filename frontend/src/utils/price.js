/**
 * 요금 계산은 서버가 최종 판단합니다(FR-RESV-05).
 * 화면 계산은 결제 전 예상 금액을 보여주기 위한 것이며,
 * 확정 금액은 항상 서버 응답의 totalAmount 를 사용합니다.
 */

import { countSlots } from './slot';

/** 30분당 요금 × 슬롯 수 */
export function calculateTotal(pricePerSlot, slotCount) {
  if (!pricePerSlot || !slotCount) return 0;
  return pricePerSlot * slotCount;
}

export function calculateTotalByRange(pricePerSlot, startTime, endTime) {
  return calculateTotal(pricePerSlot, countSlots(startTime, endTime));
}

/** 15000 → '15,000원' */
export function formatWon(amount) {
  if (amount === null || amount === undefined) return '-';
  return `${Number(amount).toLocaleString('ko-KR')}원`;
}

/** 5000 → '30분당 5,000원' */
export function formatPricePerSlot(pricePerSlot) {
  return `30분당 ${Number(pricePerSlot).toLocaleString('ko-KR')}원`;
}

/** 화면 계산값과 서버 확정값이 다른지 확인 (다르면 서버 값을 신뢰) */
export function isAmountMismatch(clientAmount, serverAmount) {
  if (clientAmount === null || serverAmount === null) return false;
  return Number(clientAmount) !== Number(serverAmount);
}

/** 크레딧은 1크레딧 = 1원이며 별도 환율이 없다(core-domain-decisions.md 1-2). 표시만 구분한다. */
export function formatCredit(amount) {
  if (amount === null || amount === undefined) return '-';
  return `${Number(amount).toLocaleString('ko-KR')} 크레딧`;
}
