import type { ReservationSummary, ReservationStatus } from '../types/api';
import { reservationEndTime } from './reservationKey';
import { CHECK_IN_DEADLINE_MINUTES } from '../constants/enums';

export const HOME_RESERVATION_STATUSES = ['HELD', 'IN_USE', 'CONFIRMED'] as const satisfies readonly ReservationStatus[];

function timestamp(reservation: ReservationSummary, field: 'startTime' | 'endTime') {
  const value = reservation[field];
  return reservationEndTime(value.includes('T') ? value : `${reservation.date}T${value}`);
}

export function selectHomeReservations(rows: ReservationSummary[], now = Date.now()): ReservationSummary[] {
  const unique = [...new Map(rows.map((row) => [row.reservationId, row])).values()];
  const groups = HOME_RESERVATION_STATUSES.map((status) => unique.filter((row) => {
    if (row.status !== status || timestamp(row, 'endTime') <= now) return false;
    if (status === 'HELD' && row.holdExpiresAt && reservationEndTime(row.holdExpiresAt) <= now) return false;
    if (status === 'CONFIRMED' && timestamp(row, 'startTime') + CHECK_IN_DEADLINE_MINUTES * 60000 < now) return false;
    return true;
  }).sort((a, b) => {
    if (status === 'HELD' && a.holdExpiresAt && b.holdExpiresAt) {
      return reservationEndTime(a.holdExpiresAt) - reservationEndTime(b.holdExpiresAt);
    }
    return timestamp(a, 'startTime') - timestamp(b, 'startTime') || a.reservationId - b.reservationId;
  }));
  // Show one of each relevant state first; fill spare slots with the next bookings.
  const selected = groups.flatMap((group) => group.slice(0, 1));
  return [...selected, ...groups.flatMap((group) => group.slice(1))].slice(0, 3);
}
