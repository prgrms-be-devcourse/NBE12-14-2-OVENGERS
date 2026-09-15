import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routePaths';
import LoadingSpinner from '../components/common/LoadingSpinner';

export default function AdminRoute() {
  const { isAuthenticated, isAdmin, initializing } = useAuth();

  if (initializing) return <LoadingSpinner label="권한을 확인하고 있습니다…" />;
  if (!isAuthenticated) return <Navigate to={ROUTES.login} replace />;
  if (!isAdmin) return <Navigate to={ROUTES.home} replace />;
  return <Outlet />;
}
