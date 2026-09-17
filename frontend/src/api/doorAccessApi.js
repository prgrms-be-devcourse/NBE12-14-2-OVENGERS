import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';

/**
 * 출입 키(도어 토큰) 발급·재발급. 예약자 본인만 호출할 수 있다.
 * 발급에는 시간 제한이 없다 — CONFIRMED 또는 IN_USE 이고 아직 종료 전이면 언제든 발급할 수 있다
 * (발급은 입장 권한이 아니라 신분증을 받는 것일 뿐이다. core-domain-decisions.md 8-1).
 * 응답의 토큰 원문은 이때 한 번만 내려오므로 다시 조회할 수 없다.
 */
export function issueDoorToken(reservationId) {
  return api.post(API_ROUTES.reservations.doorToken(reservationId));
}

/**
 * Mock Door 단말의 출입 검증. 서버가 로그인 회원이 예약자 본인인지도 확인하므로 인증이 필요하며,
 * 허용·거절 모두 서버에 기록된다(api-spec.md 7-2).
 */
export function verifyAccess({ token, spaceId }) {
  return api.post(API_ROUTES.access.verify, { token, spaceId });
}
