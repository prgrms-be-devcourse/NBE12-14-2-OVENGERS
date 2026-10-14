import { Suspense } from 'react';
import ChargeFailPage from '@/views/credit/ChargeFailPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '크레딧 충전 실패' };

export default function Page() {
  return (
    <Suspense fallback={<LoadingSpinner label="결제 결과를 불러오는 중입니다…" />}>
      <ChargeFailPage />
    </Suspense>
  );
}
