/** 프론트엔드 라우트 경로. 결제 확인 화면은 HOLD 도입으로 추가된 별도 화면입니다. */
export const ROUTES = {
  home: '/',
  login: '/login',
  signup: '/signup',

  spaces: '/spaces',
  spaceDetail: (spaceId: number | string) => `/spaces/${spaceId}`,

  reservations: '/reservations',
  reservationDetail: (reservationId: number | string) => `/reservations/${reservationId}`,
  reservationPayment: (reservationId: number | string) => `/reservations/${reservationId}/payment`,

  inquiries: '/inquiries',
  inquiryNew: '/inquiries/new',
  inquiryDetail: (id: number | string) => `/inquiries/detail?id=${id}`,
  adminInquiries: '/admin/inquiries',
  adminInquiryDetail: (id: number | string) => `/admin/inquiries/detail?id=${id}`,

  door: '/door',

  adminDashboard: '/admin/dashboard',
  adminSpaces: '/admin/spaces',
  adminSpaceNew: '/admin/spaces/new',
  adminSpaceEdit: (spaceId: number | string) => `/admin/spaces/${spaceId}/edit`,
  adminReservations: '/admin/reservations',
  adminReservationDetail: (reservationId: number | string) => `/admin/reservations/${reservationId}`,
  adminMembers: '/admin/members',
};

export interface NavItem {
  href: string;
  label: string;
  requiresAuth?: boolean;
}

/** 상단 메뉴 (일반 사용자) */
export const USER_NAV: NavItem[] = [
  { href: ROUTES.spaces, label: '오피스 찾기' },
  { href: ROUTES.door, label: '모의 출입' },
];

/** 좌측 메뉴 (관리자) */
export const ADMIN_NAV: NavItem[] = [
  { href: ROUTES.adminDashboard, label: '대시보드' },
  { href: ROUTES.adminInquiries, label: '문의 관리' },
  { href: ROUTES.adminSpaces, label: '공간 관리' },
  { href: ROUTES.adminReservations, label: '예약 관리' },
  { href: ROUTES.adminMembers, label: '회원 관리' },
];
