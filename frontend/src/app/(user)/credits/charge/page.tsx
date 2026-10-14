import { Suspense } from 'react';
import ChargePage from '@/views/credit/ChargePage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '크레딧 충전' };

export default function Page() {
  return (
    <Suspense fallback={<LoadingSpinner label="충전 화면을 불러오는 중입니다…" />}>
      <ChargePage />
    </Suspense>
  );
}
