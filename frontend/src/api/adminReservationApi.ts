import type {
  AdminReservation,
  Page,
  ReservationStatus,
} from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api, { type QueryParams } from './client';

export interface AdminReservationListParams {
  page?: number;
  size?: number;
  date?: string;
  spaceId?: number | string;
  status?: ReservationStatus | '';
}

/**
 * 관리자 예약 목록 행 DTO. 백엔드 AdminReservationResponse 규격과 1:1 일치합니다.
 */
export interface AdminReservationRow {
  reservationId: number;
  memberId: number;
  memberEmail: string;
  spaceId: number;
  spaceName: string;
  startTime: string;
  endTime: string;
  status: ReservationStatus;
  totalAmount: number;
  createdAt: string;
}

/** 서버 응답이 유효한 PageResponse 규격인지 검증하는 어댑터 함수 */
export function assertPageResponse<T>(data: unknown): Page<T> {
  const pageData = data as Partial<Page<T>> | null;
  if (
    !pageData ||
    typeof pageData !== 'object' ||
    !Array.isArray(pageData.content) ||
    typeof pageData.page !== 'number' ||
    !Number.isInteger(pageData.page) ||
    pageData.page < 0 ||
    typeof pageData.size !== 'number' ||
    !Number.isInteger(pageData.size) ||
    pageData.size <= 0 ||
    typeof pageData.totalPages !== 'number' ||
    !Number.isInteger(pageData.totalPages) ||
    pageData.totalPages < 0 ||
    typeof pageData.totalElements !== 'number' ||
    !Number.isInteger(pageData.totalElements) ||
    pageData.totalElements < 0
  ) {
    throw new Error('올바르지 않은 페이지 응답 형식입니다.');
  }
  return data as Page<T>;
}

export async function getAdminReservations({
  page = 0,
  size = 20,
  date,
  spaceId,
  status,
}: AdminReservationListParams = {}): Promise<Page<AdminReservationRow>> {
  const query: QueryParams = { page, size };

  if (date && date.trim() !== '') {
    query.date = date.trim();
  }
  if (spaceId !== undefined && spaceId !== '') {
    const parsedSpaceId = Number(spaceId);
    if (!Number.isNaN(parsedSpaceId) && parsedSpaceId > 0) {
      query.spaceId = parsedSpaceId;
    }
  }
  if (status) {
    query.status = status;
  }

  const response = await api.get<Page<AdminReservationRow>>(API_ROUTES.admin.reservations, { query });
  return assertPageResponse<AdminReservationRow>(response);
}

/** 예약 상세 + 상태 변경 이력 + 출입 시도 이력(FR-RESV-26). */
export function getAdminReservation(reservationId: number | string): Promise<AdminReservation> {
  return api.get<AdminReservation>(API_ROUTES.admin.reservation(reservationId));
}

/** 강제 취소. 사유(1~500자)가 필수이며 감사 로그에 기록됩니다(FR-RESV-27). */
export function forceCancelReservation(
  reservationId: number | string,
  reason: string,
): Promise<void> {
  return api.post<void>(API_ROUTES.admin.forceCancel(reservationId), { reason });
}
