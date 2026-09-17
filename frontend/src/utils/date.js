/** 날짜·시각 표기. 기준 시간대는 Asia/Seoul 이며 서버와 동일합니다. */

export const TIME_ZONE = 'Asia/Seoul';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const pad = (n) => String(n).padStart(2, '0');

/** Date → '2026-09-17' */
export function toDateString(date) {
  const d = date instanceof Date ? date : new Date(date);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Date → '14:00' */
export function toTimeString(date) {
  const d = date instanceof Date ? date : new Date(date);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** '2026-09-17' + '14:00' → Date */
export function toDate(dateString, timeString = '00:00') {
  const [y, m, d] = dateString.split('-').map(Number);
  const [hh, mm] = timeString.split(':').map(Number);
  return new Date(y, m - 1, d, hh, mm, 0, 0);
}

export function today() {
  return toDateString(new Date());
}

export function addDays(dateString, days) {
  const d = toDate(dateString);
  d.setDate(d.getDate() + days);
  return toDateString(d);
}

/** '2026-09-17' → '2026년 9월 17일 (목)' */
export function formatDateLabel(dateString) {
  if (!dateString) return '';
  const d = toDate(dateString);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`;
}

/** '2026-09-17' → '9/17 (목)' */
export function formatDateShort(dateString) {
  if (!dateString) return '';
  const d = toDate(dateString);
  return `${d.getMonth() + 1}/${d.getDate()} (${WEEKDAYS[d.getDay()]})`;
}

/** ISO-8601 문자열 → '2026-09-17 14:00' */
export function formatDateTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return '';
  return `${toDateString(d)} ${toTimeString(d)}`;
}

/** '14:00' ~ '15:30' 표기 */
export function formatTimeRange(startTime, endTime) {
  if (!startTime || !endTime) return '';
  return `${startTime} ~ ${endTime}`;
}

/** 예약의 이용 시간(분) */
export function minutesBetween(startTime, endTime) {
  const [sh, sm] = startTime.split(':').map(Number);
  const [eh, em] = endTime.split(':').map(Number);
  return eh * 60 + em - (sh * 60 + sm);
}

/** 90 → '1시간 30분' */
export function formatDuration(minutes) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h && m) return `${h}시간 ${m}분`;
  if (h) return `${h}시간`;
  return `${m}분`;
}

/** 남은 시간을 사람이 읽는 문구로. 지난 시각이면 null */
export function formatTimeUntil(target, now = new Date()) {
  const t = target instanceof Date ? target : new Date(target);
  const diff = Math.floor((t.getTime() - now.getTime()) / 60000);
  if (diff <= 0) return null;
  if (diff < 60) return `${diff}분 후`;
  const h = Math.floor(diff / 60);
  if (h < 24) return `${h}시간 후`;
  return `${Math.floor(h / 24)}일 후`;
}
