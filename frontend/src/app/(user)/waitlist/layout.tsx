import type { ReactNode } from 'react';
import RequireAuth from '@/components/auth/RequireAuth';

/** 빈자리 알림은 로그인이 필요합니다. 최종 권한 판단은 서버가 합니다. */
export default function WaitlistLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
