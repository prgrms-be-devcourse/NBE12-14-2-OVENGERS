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
export async function collectDashboardPages<T>(fetchPage: (page: number) => Promise<Paged<T>>): Promise<T[]> {
  const first = await fetchPage(0);
  const rows = [...first.content];
  for (let page = 1; page < first.totalPages; page++) rows.push(...(await fetchPage(page)).content);
  return rows;
}
export async function getAdminDashboard() {
  const [reservations, spaces] = await Promise.all([
    collectDashboardPages(page => api.get<Page<DashboardReservation>>(API_ROUTES.admin.reservations, { query: { page, size: 100, sort: 'id,desc' } })),
    collectDashboardPages<Space>(page => getAdminSpaces({ page, size: 100 })),
  ]);
  return { reservations: [...new Map(reservations.map(row => [row.reservationId, row])).values()], spaces, fetchedAt: Date.now() };
}
