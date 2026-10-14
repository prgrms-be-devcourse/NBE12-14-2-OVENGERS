import type { ChargeOrder } from '../types/api';

const MIN_CHARGE_AMOUNT = 1_000;
const MAX_CHARGE_AMOUNT = 100_000;
const CHARGE_UNIT = 100;

export class PollCancelledError extends Error {
  constructor() {
    super('충전 주문 상태 조회가 취소되었습니다.');
    this.name = 'PollCancelledError';
  }
}
export function normalizeReturnTo(
  value: string | null | undefined,
  fallback = '/mypage',
): string {
  if (!value) return fallback;
  const origin = typeof window === 'undefined'
    ? 'http://localhost'
    : window.location.origin;
  try {
    const url = new URL(value, origin);
    if (url.origin !== origin) return fallback;
    const path = `${url.pathname}${url.search}${url.hash}`;
    // `/..//evil.example` 처럼 정규화된 경로가 `//`로 시작하면 protocol-relative URL이
    // 되어 외부 오리진으로 나간다. 오리진 검사는 입력만 보므로 반환값을 한 번 더 막는다.
    return path.startsWith('//') ? fallback : path;
  } catch {
    return fallback;
  }
}

export function suggestChargeAmount(
  requiredAmount?: number,
  balance?: number,
): number {
  const deficit =
    Number.isFinite(requiredAmount) && Number.isFinite(balance)
      ? Math.max((requiredAmount ?? 0) - (balance ?? 0), 0)
      : 0;
  const rounded = Math.ceil(deficit / CHARGE_UNIT) * CHARGE_UNIT;
  return Math.min(MAX_CHARGE_AMOUNT, Math.max(MIN_CHARGE_AMOUNT, rounded));
}

export function buildChargeRedirectUrl(
  origin: string,
  pathname: string,
  returnTo: string | null | undefined,
): string {
  const url = new URL(pathname, origin);
  url.searchParams.set('returnTo', normalizeReturnTo(returnTo));
  return url.toString();
}

export async function pollChargeOrder({
  getOrder,
  wait,
  maxAttempts,
  isActive = () => true,
}: {
  getOrder: () => Promise<ChargeOrder>;
  wait: () => Promise<void>;
  maxAttempts: number;
  isActive?: () => boolean;
}): Promise<ChargeOrder> {
  let latest: ChargeOrder | null = null;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (!isActive()) throw new PollCancelledError();
    latest = await getOrder();
    if (latest.status !== 'CONFIRMING') return latest;
    if (attempt < maxAttempts - 1) {
      await wait();
      if (!isActive()) throw new PollCancelledError();
    }
  }
  if (!latest) throw new Error('충전 주문을 조회하지 못했습니다.');
  return latest;
}
