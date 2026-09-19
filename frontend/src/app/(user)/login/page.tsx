import { Suspense } from 'react';
import LoginPage from '@/views/auth/LoginPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '로그인' };

export default function Page() {
  // LoginPage 가 useSearchParams 로 ?redirect= 를 읽으므로 Suspense 경계가 필요합니다.
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <LoginPage />
    </Suspense>
  );
}
