import AdminSpaceFormPage from '@/views/admin/AdminSpaceFormPage';

export const metadata = { title: '공간 수정' };

// 정적 export(S3 + CloudFront) 빌드용 자리표시자입니다. 실제 ID 는 클라이언트에서 읽습니다.
export function generateStaticParams() {
  return [{ spaceId: '_' }];
}

export default function Page() {
  return <AdminSpaceFormPage mode="edit" />;
}
