/** 날짜·시각 표기. 기준 시간대는 Asia/Seoul 이며 서버와 동일합니다. */

export const TIME_ZONE = 'Asia/Seoul';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

const pad = (n: number): string => String(n).padStart(2, '0');

/** Date → '2026-09-17' */
export function toDateString(date: Date | string | number): string {
  const d = date instanceof Date ? date : new Date(date);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Date → '14:00' */
export function toTimeString(date: Date | string | number): string {
  const d = date instanceof Date ? date : new Date(date);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** '2026-09-17' + '14:00' → Date */
export function toDate(dateString: string, timeString = '00:00'): Date {
  const [y = 0, m = 1, d = 1] = dateString.split('-').map(Number);
  const [hh = 0, mm = 0] = timeString.split(':').map(Number);
  return new Date(y, m - 1, d, hh, mm, 0, 0);
}

export function today(): string {
  return toDateString(new Date());
}

export function addDays(dateString: string, days: number): string {
  const d = toDate(dateString);
  d.setDate(d.getDate() + days);
  return toDateString(d);
}

/** '2026-09-17' → '2026년 9월 17일 (목)' */
export function formatDateLabel(dateString: string | null | undefined): string {
  if (!dateString) return '';
  const d = toDate(dateString);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`;
}

/** '2026-09-17' → '9/17 (목)' */
export function formatDateShort(dateString: string | null | undefined): string {
  if (!dateString) return '';
  const d = toDate(dateString);
  return `${d.getMonth() + 1}/${d.getDate()} (${WEEKDAYS[d.getDay()]})`;
}

/** ISO-8601 문자열 → '2026-09-17 14:00' */
export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return '';
  return `${toDateString(d)} ${toTimeString(d)}`;
}

/** '14:00' ~ '15:30' 표기 */
export function formatTimeRange(
  startTime: string | null | undefined,
  endTime: string | null | undefined,
): string {
  if (!startTime || !endTime) return '';
  return `${startTime} ~ ${endTime}`;
}

/** 예약의 이용 시간(분) */
export function minutesBetween(startTime: string, endTime: string): number {
  const [sh = 0, sm = 0] = startTime.split(':').map(Number);
  const [eh = 0, em = 0] = endTime.split(':').map(Number);
  return eh * 60 + em - (sh * 60 + sm);
}

/** 90 → '1시간 30분' */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h && m) return `${h}시간 ${m}분`;
  if (h) return `${h}시간`;
  return `${m}분`;
}

/** 남은 시간을 사람이 읽는 문구로. 지난 시각이면 null */
export function formatTimeUntil(target: Date | string, now = new Date()): string | null {
  const t = target instanceof Date ? target : new Date(target);
  const diff = Math.floor((t.getTime() - now.getTime()) / 60000);
  if (diff <= 0) return null;
  if (diff < 60) return `${diff}분 후`;
  const h = Math.floor(diff / 60);
  if (h < 24) return `${h}시간 후`;
  return `${Math.floor(h / 24)}일 후`;
}
