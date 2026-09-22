import SpaceDetailPage from '@/views/space/SpaceDetailPage';

export const metadata = { title: '공간 상세' };

// 정적 export(S3 + CloudFront) 빌드용 자리표시자입니다. 실제 ID 는 클라이언트에서 읽습니다.
export function generateStaticParams() {
  return [{ spaceId: '_' }];
}

export default function Page() {
  return <SpaceDetailPage />;
}
