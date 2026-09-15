import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routePaths';
import LoadingSpinner from '../components/common/LoadingSpinner';

/**
 * 로그인하지 않은 접근을 로그인 화면으로 보냅니다.
 * 화면 차단은 편의일 뿐이고 실제 권한 판단은 서버가 합니다.
 */
export default function ProtectedRoute() {
  const { isAuthenticated, initializing } = useAuth();
  const location = useLocation();

  if (initializing) return <LoadingSpinner label="로그인 상태를 확인하고 있습니다…" />;
  if (!isAuthenticated) {
    return <Navigate to={ROUTES.login} replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
