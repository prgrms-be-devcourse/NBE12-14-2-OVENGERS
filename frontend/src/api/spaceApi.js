import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

/** 공간 목록. 비회원도 조회할 수 있습니다(FR-SPACE-01). */
export function getSpaces({ page = 0, size = 20, keyword, status } = {}) {
  return api.get(API_ROUTES.spaces.list, {
    query: { page, size, keyword, status },
    auth: false,
  });
}

export function getSpace(spaceId) {
  return api.get(API_ROUTES.spaces.detail(spaceId), { auth: false });
}

/** 날짜별 30분 슬롯 가용성(FR-SPACE-03). */
export function getSpaceSlots(spaceId, date) {
  return api.get(API_ROUTES.spaces.slots(spaceId), { query: { date }, auth: false });
}
