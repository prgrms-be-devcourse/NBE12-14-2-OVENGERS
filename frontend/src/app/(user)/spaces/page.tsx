import SpaceListPage from '@/views/space/SpaceListPage';

export const metadata = {
  title: '공간 찾기',
  description: '날짜를 고르면 그날의 예약 가능한 시간을 함께 볼 수 있습니다.',
};

export default function Page() {
  return <SpaceListPage />;
}
