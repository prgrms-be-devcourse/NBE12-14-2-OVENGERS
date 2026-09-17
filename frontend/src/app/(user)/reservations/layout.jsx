import RequireAuth from '@/components/auth/RequireAuth';

/** 예약 관련 화면은 모두 로그인이 필요합니다. 최종 권한 판단은 서버가 합니다. */
export default function ReservationsLayout({ children }) {
  return <RequireAuth>{children}</RequireAuth>;
}
