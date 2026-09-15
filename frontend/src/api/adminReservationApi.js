import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

export function getAdminReservations({ page = 0, size = 20, date, spaceId, status } = {}) {
  return api.get(API_ROUTES.admin.reservations, {
    query: { page, size, date, spaceId, status },
  });
}

/** 예약 상세 + 상태 변경 이력 + 출입 시도 이력(FR-RESV-26). */
export function getAdminReservation(reservationId) {
  return api.get(API_ROUTES.admin.reservation(reservationId));
}

/** 강제 취소. 사유(1~500자)가 필수이며 감사 로그에 기록됩니다(FR-RESV-27). */
export function forceCancelReservation(reservationId, reason) {
  return api.post(API_ROUTES.admin.forceCancel(reservationId), { reason });
}
