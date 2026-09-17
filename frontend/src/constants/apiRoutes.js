/**
 * 백엔드 엔드포인트 경로를 한곳에 모읍니다.
 *
 * 기준 문서: docs/api-spec.md (2026-09-15, core-domain-decisions.md 확정본 반영).
 * 화면·API 모듈에서 경로 문자열을 직접 쓰지 않는 이유는, 경로가 바뀌어도 이 파일만
 * 고치면 되도록 하기 위함입니다.
 */

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export const API_ROUTES = {
  auth: {
    signup: '/auth/signup',
    login: '/auth/login',
    refresh: '/auth/refresh',
    logout: '/auth/logout',
  },
  members: {
    me: '/members/me',
  },
  spaces: {
    list: '/spaces',
    detail: (spaceId) => `/spaces/${spaceId}`,
    slots: (spaceId) => `/spaces/${spaceId}/slots`,
  },
  reservations: {
    // 예약 생성 = 슬롯 확보(HOLD). 결제는 아직 일어나지 않는다(api-spec.md 5-1).
    create: '/reservations',
    list: '/reservations',
    detail: (reservationId) => `/reservations/${reservationId}`,
    // 결제 확인 및 확정. Idempotency-Key 필수(api-spec.md 5-2).
    pay: (reservationId) => `/reservations/${reservationId}/pay`,
    // 취소는 POST .../cancel. 시작 전 CONFIRMED 상태에서만 가능(api-spec.md 5-4).
    cancel: (reservationId) => `/reservations/${reservationId}/cancel`,
    extend: (reservationId) => `/reservations/${reservationId}/extend`,
    checkOut: (reservationId) => `/reservations/${reservationId}/check-out`,
    doorToken: (reservationId) => `/reservations/${reservationId}/door-token`,
  },
  access: {
    // Mock Door 검증. 로그인 필요 — 서버가 예약자 본인 여부도 확인한다(api-spec.md 7).
    verify: '/door-access/verify',
  },
  admin: {
    spaces: '/admin/spaces',
    space: (spaceId) => `/admin/spaces/${spaceId}`,
    reservations: '/admin/reservations',
    reservation: (reservationId) => `/admin/reservations/${reservationId}`,
    forceCancel: (reservationId) => `/admin/reservations/${reservationId}/force-cancel`,
    members: '/admin/members',
    memberSuspend: (memberId) => `/admin/members/${memberId}/suspend`,
    memberRestore: (memberId) => `/admin/members/${memberId}/restore`,
    // 크레딧 지급(ADMIN_GRANT). 회수 없음, 자기 자신 지급 불가(core-domain-decisions.md 1-3).
    memberCredit: (memberId) => `/admin/members/${memberId}/credits`,
  },
};

/** 인증 헤더를 붙이지 않는 경로 (비회원도 호출 가능) */
export const PUBLIC_PATH_PREFIXES = ['/auth/', '/spaces'];
