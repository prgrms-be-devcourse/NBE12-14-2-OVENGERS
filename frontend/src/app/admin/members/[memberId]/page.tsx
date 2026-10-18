import AdminMemberDetailPage from '@/views/admin/AdminMemberDetailPage';

export const metadata = {
    title: '회원 상세',
};

// 정적 배포 시 동적 경로를 처리하기 위한 자리표시자
export function generateStaticParams() {
    return [{ memberId: '_' }];
}

export default function Page() {
    return <AdminMemberDetailPage />;
}
