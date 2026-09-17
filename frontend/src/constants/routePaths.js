/** 프론트엔드 라우트 경로. 결제 확인 화면은 HOLD 도입으로 추가된 별도 화면입니다. */
export const ROUTES = {
  home: '/',
  login: '/login',
  signup: '/signup',

  spaces: '/spaces',
  spaceDetail: (spaceId = ':spaceId') => `/spaces/${spaceId}`,

  reservations: '/reservations',
  reservationDetail: (reservationId = ':reservationId') => `/reservations/${reservationId}`,
  reservationPayment: (reservationId = ':reservationId') =>
    `/reservations/${reservationId}/payment`,

  door: '/door',

  adminSpaces: '/admin/spaces',
  adminSpaceNew: '/admin/spaces/new',
  adminSpaceEdit: (spaceId = ':spaceId') => `/admin/spaces/${spaceId}/edit`,
  adminReservations: '/admin/reservations',
  adminReservationDetail: (reservationId = ':reservationId') =>
    `/admin/reservations/${reservationId}`,
  adminMembers: '/admin/members',
};

/** 상단 메뉴 (일반 사용자) */
export const USER_NAV = [
  { to: ROUTES.spaces, label: '공간 찾기' },
  { to: ROUTES.reservations, label: '내 예약', requiresAuth: true },
  { to: ROUTES.door, label: '모의 출입' },
];

/** 좌측 메뉴 (관리자) */
export const ADMIN_NAV = [
  { to: ROUTES.adminSpaces, label: '공간 관리' },
  { to: ROUTES.adminReservations, label: '예약 관리' },
  { to: ROUTES.adminMembers, label: '회원 관리' },
];
