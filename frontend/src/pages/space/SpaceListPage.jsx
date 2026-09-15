import { useCallback, useState } from 'react';
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
  const [filter, setFilter] = useState({ date: today(), keyword: '' });
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
          <h1>공간 찾기</h1>
          <p>날짜를 고르면 그날의 예약 가능한 시간을 함께 볼 수 있습니다.</p>
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

      {spaces.length > 0 && (
        <>
          <div className="grid3">
            {spaces.map((space) => (
              <SpaceCard key={space.id} space={space} availability={space.availability} />
            ))}
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
