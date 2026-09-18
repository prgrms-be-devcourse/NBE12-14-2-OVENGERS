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

/** 실제 백엔드 슬롯 응답 */
interface SpaceSlotsApiResponse {
  spaceId: number;
  date: string;
  slots: {
    slotStart: string;
    slotEnd: string;
    isAvailable: boolean;
  }[];
}

/** 날짜별 30분 슬롯 가용성 */
export async function getSpaceSlots(
  spaceId: number | string,
  date: string,
): Promise<SpaceSlotsResponse> {
  const response = await api.get<SpaceSlotsApiResponse>(
    API_ROUTES.spaces.slots(spaceId),
    {
      query: { date },
      auth: false,
    },
  );

  return {
    spaceId: response.spaceId,
    date: response.date,
    slots: response.slots.map((slot) => ({
      startTime: slot.slotStart.slice(11, 16),
      endTime: slot.slotEnd.slice(11, 16),
      available: slot.isAvailable,
    })),
  };
}

