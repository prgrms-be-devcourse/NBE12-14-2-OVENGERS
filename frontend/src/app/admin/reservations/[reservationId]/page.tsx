import AdminReservationDetailPage from '@/views/admin/AdminReservationDetailPage';

export const metadata = { title: '예약 상세' };

// 정적 export(S3 + CloudFront) 빌드용 자리표시자입니다. 실제 ID 는 클라이언트에서 읽습니다.
export function generateStaticParams() {
  return [{ reservationId: '_' }];
}

export default function Page() {
  return <AdminReservationDetailPage />;
}
