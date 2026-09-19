'use client';

import Link from 'next/link';
import { ROUTES } from '../constants/routePaths';
import EmptyState from '../components/common/EmptyState';

export default function NotFoundPage() {
  return (
    <EmptyState
      title="페이지를 찾을 수 없습니다"
      description="주소를 확인하거나 메인에서 원하는 공간을 다시 찾아보세요."
      action={
        <Link href={ROUTES.home} className="btn primary">
          메인으로 돌아가기
        </Link>
      }
    />
  );
}
