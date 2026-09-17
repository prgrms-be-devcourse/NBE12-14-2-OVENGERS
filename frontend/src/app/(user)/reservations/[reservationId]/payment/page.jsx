import { Suspense } from 'react';
import PaymentPage from '@/views/reservation/PaymentPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '결제하기' };

export default function Page() {
  // PaymentPage 가 useSearchParams 로 ?spaceVersion= 을 읽으므로 Suspense 경계가 필요합니다.
  return (
    <Suspense fallback={<LoadingSpinner label="결제 정보를 불러오는 중입니다…" />}>
      <PaymentPage />
    </Suspense>
  );
}
