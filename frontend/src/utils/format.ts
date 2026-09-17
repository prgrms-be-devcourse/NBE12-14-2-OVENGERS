/** 화면 표기용 문자열 도우미 */

type ClassValue = string | false | null | undefined;

/** 조건부 className 조합: cx('btn', isPrimary && 'primary') */
export function cx(...values: ClassValue[]): string {
  return values.filter(Boolean).join(' ');
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-';
  return Number(value).toLocaleString('ko-KR');
}

/** 'user@example.com' → 'us***@example.com' (관리자 목록에서 사용) */
export function maskEmail(email: string | null | undefined): string {
  if (!email || !email.includes('@')) return email ?? '';
  const [local = '', domain = ''] = email.split('@');
  const head = local.slice(0, 2);
  return `${head}${'*'.repeat(Math.max(local.length - 2, 1))}@${domain}`;
}

export function truncate(text: string | null | undefined, length = 40): string {
  if (!text) return '';
  return text.length > length ? `${text.slice(0, length)}…` : text;
}

/** 예약 번호 표기: 1001 → 'SK-1001' */
export function formatReservationNo(reservationId: number | string | null | undefined): string {
  return reservationId ? `SK-${reservationId}` : '-';
}

/** 출입 키 원문을 4글자씩 끊어 읽기 쉽게 */
export function formatAccessKey(key: string | null | undefined): string {
  if (!key) return '';
  return key.replace(/(.{4})/g, '$1 ').trim();
}
