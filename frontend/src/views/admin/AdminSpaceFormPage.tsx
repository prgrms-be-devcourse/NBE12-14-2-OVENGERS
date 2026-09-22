'use client';

import { useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { SpacePayload } from '../../api/adminSpaceApi';
import { createSpace, getAdminSpace, updateSpace } from '../../api/adminSpaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import SpaceForm from '../../components/space/SpaceForm';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import { useRouteId } from '@/hooks/useRouteId';

export default function AdminSpaceFormPage({ mode = 'create' }: { mode?: 'create' | 'edit' }) {
  const spaceId = useRouteId('spaceId');
  const router = useRouter();
  const isEdit = mode === 'edit';

  const fetchSpace = useCallback(
    () => (isEdit ? getAdminSpace(spaceId) : Promise.resolve(null)),
    [isEdit, spaceId],
  );
  const { data: space, loading, error } = useAsync(fetchSpace, [fetchSpace], { immediate: isEdit });

  const submit = useAction(async (payload: SpacePayload) => {
    if (isEdit) await updateSpace(spaceId, payload);
    else await createSpace(payload);
    router.push(ROUTES.adminSpaces);
  });

  if (isEdit && loading) return <LoadingSpinner />;
  if (isEdit && error) return <ErrorMessage error={error} />;

  return (
    <>
      <nav className="crumb" aria-label="현재 위치">
        <Link href={ROUTES.adminSpaces}>공간 관리</Link>
        <span>›</span>
        <span>{isEdit ? '공간 수정' : '공간 등록'}</span>
      </nav>

      <div className="pagehead">
        <div>
          <p className="page-kicker">OFFICE DETAILS</p>
          <h1>{isEdit ? '공간 수정' : '공간 등록'}</h1>
          <p>
            {isEdit
              ? '요금을 바꿔도 이미 확정된 예약의 금액은 유지됩니다.'
              : '공간 정보와 이용 조건을 입력해 주세요.'}
          </p>
        </div>
      </div>

      <SpaceForm
        mode={mode}
        initialValue={space ?? undefined}
        submitting={submit.loading}
        error={submit.error}
        onSubmit={(payload) => submit.execute(payload).catch(() => {})}
        onCancel={() => router.push(ROUTES.adminSpaces)}
      />
    </>
  );
}
