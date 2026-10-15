import type { ApplyWaitlistRequest, WaitlistEntry } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import { toLocalDateTime } from '../utils/waitlist';
import api from './client';

/**
 * 대기 신청. 구간은 슬롯 선택과 같은 (date, startTime, endTime)로 받아 서버의 LocalDateTime 으로 바꿔 보낸다.
 * 서버가 점유 여부·한도(3건)·중복·본인 예약 겹침을 판단하며, 실패는 WAITLIST_* 코드로 온다.
 */
export function applyWaitlist({
  spaceId,
  date,
  startTime,
  endTime,
}: ApplyWaitlistRequest): Promise<WaitlistEntry> {
  return api.post<WaitlistEntry>(API_ROUTES.waitlist.create, {
    spaceId,
    startTime: toLocalDateTime(date, startTime),
    endTime: toLocalDateTime(date, endTime),
  });
}

/** 내 활성 대기(WAITING, 시작 전) 목록. 신청 순서 오름차순. */
export function getMyWaitlist(): Promise<WaitlistEntry[]> {
  return api.get<WaitlistEntry[]>(API_ROUTES.waitlist.mine);
}

/** 본인 대기 취소. 이미 취소된 건도 서버가 멱등하게 204 로 응답한다. */
export function cancelWaitlist(waitlistEntryId: number | string): Promise<void> {
  return api.delete<void>(API_ROUTES.waitlist.entry(waitlistEntryId));
}
