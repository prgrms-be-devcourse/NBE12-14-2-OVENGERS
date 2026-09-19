import type { AdminMember, MemberStatus, Page } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import { MEMBER_STATUS } from '../constants/enums';
import api from './client';

export interface AdminMemberListParams {
  page?: number;
  size?: number;
  status?: MemberStatus | '';
  keyword?: string;
}

export function getAdminMembers({
  page = 0,
  size = 20,
  status,
  keyword,
}: AdminMemberListParams = {}): Promise<Page<AdminMember>> {
  return api.get<Page<AdminMember>>(API_ROUTES.admin.members, {
    query: { page, size, status, keyword },
  });
}

/** 사유 1~500자 필수. audit_log에 기록된다(api-spec.md 3장). */
export function suspendMember(memberId: number, reason: string): Promise<void> {
  return api.patch<void>(API_ROUTES.admin.memberSuspend(memberId), { reason });
}

export function restoreMember(memberId: number, reason: string): Promise<void> {
  return api.patch<void>(API_ROUTES.admin.memberRestore(memberId), { reason });
}

/**
 * 정지/복구는 서로 다른 엔드포인트다(신규 status 필드를 받는 공용 PATCH는 없음).
 * 화면(AdminMemberListPage)이 대상 status 만 보고 분기할 수 있도록 얇게 감싼다.
 */
export function changeMemberStatus(
  memberId: number,
  { status, reason }: { status: MemberStatus; reason: string },
): Promise<void> {
  return status === MEMBER_STATUS.SUSPENDED
    ? suspendMember(memberId, reason)
    : restoreMember(memberId, reason);
}

/**
 * 크레딧 지급(ADMIN_GRANT). 지급만 가능하며 회수는 없다.
 * 자기 자신에게는 지급할 수 없다 — 서버가 SELF_GRANT_NOT_ALLOWED(422)로 거절한다
 * (core-domain-decisions.md 1-3).
 */
export function grantMemberCredit(
  memberId: number,
  { amount, reason }: { amount: number; reason: string },
): Promise<void> {
  return api.post<void>(API_ROUTES.admin.memberCredit(memberId), { amount, reason });
}
