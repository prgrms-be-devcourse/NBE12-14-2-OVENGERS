import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';
import { getAdminSpaces } from './adminSpaceApi';
import type { Page, ReservationStatus, Space } from '../types/api';

// The admin endpoint returns Spring Page and full LocalDateTime fields.
export interface DashboardReservation {
  reservationId: number; memberId: number; memberEmail: string;
  spaceId: number; spaceName: string; startTime: string; endTime: string;
  status: ReservationStatus; totalAmount: number; createdAt: string;
}
interface Paged<T> { content: T[]; totalPages: number }

// 페이지가 많아지면(예약/공간이 쌓일수록) 순차 대기 시간이 선형으로 늘어나던 문제를
// 병렬 배치 요청으로 바꿔서 완화합니다. CONCURRENCY는 브라우저/서버에 동시 요청이
// 몰리는 걸 막기 위한 상한입니다.
const CONCURRENCY = 8;

export async function collectDashboardPages<T>(fetchPage: (page: number) => Promise<Paged<T>>): Promise<T[]> {
  const first = await fetchPage(0);
  const rows = [...first.content];
  const remaining = Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) => i + 1);
  for (let i = 0; i < remaining.length; i += CONCURRENCY) {
    const batch = remaining.slice(i, i + CONCURRENCY);
    const results = await Promise.all(batch.map(fetchPage));
    for (const result of results) rows.push(...result.content);
  }
  return rows;
}

export async function getAdminDashboard() {
  const [reservations, spaces] = await Promise.all([
    collectDashboardPages(page => api.get<Page<DashboardReservation>>(API_ROUTES.admin.reservations, { query: { page, size: 1000, sort: 'id,desc' } })),
    collectDashboardPages<Space>(page => getAdminSpaces({ page, size: 1000 })),
  ]);
  return { reservations: [...new Map(reservations.map(row => [row.reservationId, row])).values()], spaces, fetchedAt: Date.now() };
}
