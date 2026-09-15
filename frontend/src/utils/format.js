/** 화면 표기용 문자열 도우미 */

/** 조건부 className 조합: cx('btn', isPrimary && 'primary') */
export function cx(...values) {
  return values.filter(Boolean).join(' ');
}

export function formatNumber(value) {
  if (value === null || value === undefined) return '-';
  return Number(value).toLocaleString('ko-KR');
}

/** 'user@example.com' → 'us***@example.com' (관리자 목록에서 사용) */
export function maskEmail(email) {
  if (!email || !email.includes('@')) return email ?? '';
  const [local, domain] = email.split('@');
  const head = local.slice(0, 2);
  return `${head}${'*'.repeat(Math.max(local.length - 2, 1))}@${domain}`;
}

export function truncate(text, length = 40) {
  if (!text) return '';
  return text.length > length ? `${text.slice(0, length)}…` : text;
}

/** 예약 번호 표기: 1001 → 'SK-1001' */
export function formatReservationNo(reservationId) {
  return reservationId ? `SK-${reservationId}` : '-';
}

/** 출입 키 원문을 4글자씩 끊어 읽기 쉽게 */
export function formatAccessKey(key) {
  if (!key) return '';
  return key.replace(/(.{4})/g, '$1 ').trim();
}
