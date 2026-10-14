import { Suspense } from 'react';
import ChargeSuccessPage from '@/views/credit/ChargeSuccessPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '크레딧 충전 결과' };

export default function Page() {
  return (
    <Suspense fallback={<LoadingSpinner label="충전 결과를 확인하는 중입니다…" />}>
      <ChargeSuccessPage />
    </Suspense>
  );
}
