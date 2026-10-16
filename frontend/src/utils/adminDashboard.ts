import type { DashboardReservation } from '../api/adminDashboardApi';
import type { ReservationStatus } from '../types/api';
export const DASHBOARD_STATES = ['HELD', 'CONFIRMED', 'IN_USE', 'COMPLETED'] as const;
export const DASHBOARD_LABELS = { HELD: '결제 대기', CONFIRMED: '결제 완료', IN_USE: '사용 중', COMPLETED: '이용 완료' };
export function seoulDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find(p => p.type === type)?.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
export function selectDashboardReservations(rows: DashboardReservation[], date: string, spaceId = '') {
  return rows.filter(row => (!date || row.startTime.slice(0, 10) <= date && row.endTime.slice(0, 10) >= date) && (!spaceId || String(row.spaceId) === spaceId));
}
/** 서버(status-counts)가 센 일곱 상태 중 대시보드가 보여 주는 네 상태만 꺼낸다. 브라우저에서 예약을 세지 않는다. */
export function pickDashboardCounts(counts: Partial<Record<ReservationStatus, number>> | null | undefined) {
  return Object.fromEntries(DASHBOARD_STATES.map(status => [status, counts?.[status] ?? 0])) as Record<typeof DASHBOARD_STATES[number], number>;
}
const priority: Partial<Record<ReservationStatus, number>> = { IN_USE: 0, HELD: 1, CONFIRMED: 2 };
export function sortDashboardReservations(rows: DashboardReservation[]) {
  return [...rows].sort((a, b) => (priority[a.status] ?? 3) - (priority[b.status] ?? 3) || a.startTime.localeCompare(b.startTime) || a.reservationId - b.reservationId);
}
