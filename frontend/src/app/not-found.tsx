import UserShell from '@/components/layout/UserShell';
import NotFoundPage from '@/views/NotFoundPage';

export const metadata = { title: '페이지를 찾을 수 없습니다' };

export default function NotFound() {
  return (
    <UserShell>
      <NotFoundPage />
    </UserShell>
  );
}
