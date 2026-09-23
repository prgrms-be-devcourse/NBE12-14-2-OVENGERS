// A tab-scoped marker survives route reloads; never used to authorize payment.
const pending = new Map<string, number>();
const prefix = 'slotkey:payment-celebration:';
const maxAge = 5 * 60_000;

export function markPaymentCompleted(reservationId: string | number) {
  const key = String(reservationId);
  const time = Date.now();
  pending.set(key, time);
  try { window.sessionStorage.setItem(prefix + key, String(time)); } catch { /* In-memory fallback. */ }
}

export function consumePaymentCompleted(reservationId: string | number) {
  const key = String(reservationId);
  let completedAt = pending.get(key);
  pending.delete(key);
  try {
    const stored = window.sessionStorage.getItem(prefix + key);
    window.sessionStorage.removeItem(prefix + key);
    if (stored !== null) completedAt = Number(stored);
  } catch { /* In-memory fallback. */ }
  const age = completedAt === undefined ? NaN : Date.now() - completedAt;
  return Number.isFinite(age) && age >= 0 && age < maxAge;
}
