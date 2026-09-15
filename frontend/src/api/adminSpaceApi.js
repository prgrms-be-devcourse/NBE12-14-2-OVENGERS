import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

export function getAdminSpaces({ page = 0, size = 20, status, keyword } = {}) {
  return api.get(API_ROUTES.admin.spaces, { query: { page, size, status, keyword } });
}

export function getAdminSpace(spaceId) {
  return api.get(API_ROUTES.admin.space(spaceId));
}

export function createSpace(payload) {
  return api.post(API_ROUTES.admin.spaces, payload);
}

/**
 * 공간 수정. 미래 확정 예약과 충돌하는 운영시간 축소는 서버가 거절합니다(FR-SPACE-09).
 * 요금을 바꿔도 기존 예약 금액은 유지됩니다(FR-SPACE-10).
 */
export function updateSpace(spaceId, payload) {
  return api.patch(API_ROUTES.admin.space(spaceId), payload);
}
