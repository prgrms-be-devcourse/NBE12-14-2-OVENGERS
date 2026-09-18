'use client';

import { useCallback, useState } from 'react';
import type { SpaceFilterValue } from '../../components/space/SpaceFilter';
import { getSpaces } from '../../api/spaceApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { today } from '../../utils/date';
import SpaceCard from '../../components/space/SpaceCard';
import SpaceFilter from '../../components/space/SpaceFilter';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';

export default function SpaceListPage() {
  const [filter, setFilter] = useState<SpaceFilterValue>({ date: today(), keyword: '' });
  const { page, size, setPage } = usePagination({ initialSize: 9 });

  const fetchSpaces = useCallback(
    () => getSpaces({ page, size, keyword: filter.keyword, date: filter.date }),
    [page, size, filter.keyword, filter.date],
  );
  const { data, loading, error, run } = useAsync(fetchSpaces, [fetchSpaces]);

  const spaces = data?.content ?? [];

  return (
    <>
      <div className="pagehead">
        <div>
          <p className="page-kicker">FIND YOUR OFFICE</p>
          <h1>오피스 찾기</h1>
          <p>오늘의 일정과 함께할 사람에 맞는 공간을 찾아보세요.</p>
        </div>
      </div>

      <SpaceFilter
        value={filter}
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
      />

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={run} />

      {!loading && !error && spaces.length === 0 && (
        <EmptyState
          title="조건에 맞는 공간이 없습니다"
          description="검색어나 날짜를 바꾸어 다시 찾아보세요."
        />
      )}

      {!loading && !error && spaces.length > 0 && (
        <>
          <div className="grid3">
            {spaces.map((space) => (
              <SpaceCard key={space.id} space={space} availability={space.availability} />
            ))}
          </div>
          <Pagination
            page={data?.page}
            totalPages={data?.totalPages}
            totalElements={data?.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
