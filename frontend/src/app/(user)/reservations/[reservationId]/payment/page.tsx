import { Suspense } from 'react';
import PaymentPage from '@/views/reservation/PaymentPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';

export const metadata = { title: '결제하기' };

// 정적 export(S3 + CloudFront) 빌드용 자리표시자입니다. 실제 ID 는 클라이언트에서 읽습니다.
export function generateStaticParams() {
  return [{ reservationId: '_' }];
}

export default function Page() {
  // PaymentPage 가 useSearchParams 로 ?spaceVersion= 을 읽으므로 Suspense 경계가 필요합니다.
  return (
    <Suspense fallback={<LoadingSpinner label="결제 정보를 불러오는 중입니다…" />}>
      <PaymentPage />
    </Suspense>
  );
}
