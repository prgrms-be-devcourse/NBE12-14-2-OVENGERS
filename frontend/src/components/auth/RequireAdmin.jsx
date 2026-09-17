'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '../../hooks/useAuth';
import { ROUTES } from '../../constants/routePaths';
import LoadingSpinner from '../common/LoadingSpinner';

export default function RequireAdmin({ children }) {
  const { isAuthenticated, isAdmin, initializing } = useAuth();
  const router = useRouter();

  const target = initializing
    ? null
    : !isAuthenticated
      ? ROUTES.login
      : !isAdmin
        ? ROUTES.home
        : null;

  useEffect(() => {
    if (target) router.replace(target);
  }, [target, router]);

  if (initializing) return <LoadingSpinner label="권한을 확인하고 있습니다…" />;
  if (target) return <LoadingSpinner label="화면을 이동하고 있습니다…" />;
  return children;
}
