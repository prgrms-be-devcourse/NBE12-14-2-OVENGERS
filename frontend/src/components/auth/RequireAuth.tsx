'use client';

/**
 * 로그인하지 않은 접근을 로그인 화면으로 보냅니다.
 * 화면 차단은 편의일 뿐이고 실제 권한 판단은 서버가 합니다(docs/requirements.md 5장).
 */

import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '../../hooks/useAuth';
import { ROUTES } from '../../constants/routePaths';
import LoadingSpinner from '../common/LoadingSpinner';

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, initializing } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const shouldRedirect = !initializing && !isAuthenticated;

  useEffect(() => {
    if (!shouldRedirect) return;
    router.replace(`${ROUTES.login}?redirect=${encodeURIComponent(pathname + window.location.search)}`);
  }, [shouldRedirect, pathname, router]);

  if (initializing) return <LoadingSpinner label="로그인 상태를 확인하고 있습니다…" />;
  if (shouldRedirect) return <LoadingSpinner label="로그인 화면으로 이동합니다…" />;
  return children;
}
