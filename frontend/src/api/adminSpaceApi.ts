import type { Page, Space, SpaceFormValues, SpaceStatus } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api from './client';

export interface AdminSpaceListParams {
  page?: number;
  size?: number;
  status?: SpaceStatus | '';
  keyword?: string;
}

export function getAdminSpaces({
  page = 0,
  size = 20,
  status,
  keyword,
}: AdminSpaceListParams = {}): Promise<Page<Space>> {
  return api.get<Page<Space>>(API_ROUTES.admin.spaces, {
    query: { page, size, status, keyword },
  });
}

export function getAdminSpace(spaceId: number | string): Promise<Space> {
  return api.get<Space>(API_ROUTES.admin.space(spaceId));
}

/** 폼이 숫자로 정규화한 뒤 넘깁니다. 이미지는 전용 PUT API를 사용하므로 제외합니다. */
export type SpacePayload = Omit<SpaceFormValues, 'capacity' | 'pricePerSlot' | 'imagePath'> & {
  capacity: number;
  pricePerSlot: number;
};

export function createSpace(payload: SpacePayload): Promise<Space> {
  return api.post<Space>(API_ROUTES.admin.spaces, payload);
}

/**
 * 공간 수정. 미래 확정 예약과 충돌하는 운영시간 축소는 서버가 거절합니다(FR-SPACE-09).
 * 요금을 바꿔도 기존 예약 금액은 유지됩니다(FR-SPACE-10).
 */
export function updateSpace(spaceId: number | string, payload: SpacePayload): Promise<Space> {
  return api.patch<Space>(API_ROUTES.admin.space(spaceId), payload);
}

/**
 * 공간 대표 이미지 업로드.
 * PUT /api/v1/admin/spaces/{spaceId}/image 로 multipart/form-data 전송합니다.
 */
export function uploadSpaceImage(spaceId: number | string, file: File): Promise<Space> {
  const formData = new FormData();
  formData.append('file', file);
  return api.put<Space>(API_ROUTES.admin.spaceImage(spaceId), formData);
}
