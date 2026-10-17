import type { ReactNode } from 'react';
import RequireAuth from '@/components/auth/RequireAuth';

/** 베스트 랭킹은 로그인한 회원에게 보여 줍니다. 최종 권한 판단은 서버가 합니다. */
export default function RankingLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
