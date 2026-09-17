import type { Page, Space, SpaceSlotsResponse, SpaceStatus } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api from './client';

export interface SpaceListParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: SpaceStatus | '';
  date?: string;
}

/** 공간 목록. 비회원도 조회할 수 있습니다(FR-SPACE-01). */
export function getSpaces({
  page = 0,
  size = 20,
  keyword,
  status,
  date,
}: SpaceListParams = {}): Promise<Page<Space>> {
  return api.get<Page<Space>>(API_ROUTES.spaces.list, {
    query: { page, size, keyword, status, date },
    auth: false,
  });
}

export function getSpace(spaceId: number | string): Promise<Space> {
  return api.get<Space>(API_ROUTES.spaces.detail(spaceId), { auth: false });
}

/** 날짜별 30분 슬롯 가용성(FR-SPACE-03). */
export function getSpaceSlots(
  spaceId: number | string,
  date: string,
): Promise<SpaceSlotsResponse> {
  return api.get<SpaceSlotsResponse>(API_ROUTES.spaces.slots(spaceId), {
    query: { date },
    auth: false,
  });
}
