'use client';
import { useState } from 'react';
import { getSpaces } from '../../api/spaceApi';
import { useAsync } from '../../hooks/useApi';
import { EMPTY_SPACE_FILTERS, toSpaceSearchParams } from '../../utils/spaceSearch';
import type { SpaceSearchFilters } from '../../utils/spaceSearch';
import SpaceCard from '../../components/space/SpaceCard';
import SpaceSearchFilter from '../../components/space/SpaceSearchFilter';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';

export default function SpaceListPage() {
  const [filter, setFilter] = useState<SpaceSearchFilters>(EMPTY_SPACE_FILTERS);
  const [page, setPage] = useState(0);
  return <>
    <div className="pagehead"><div><h1>오피스 찾기</h1><p>지역과 요금, 이용 시간에 맞는 공간을 찾아보세요.</p></div></div>
    <SpaceSearchFilter onApply={(next) => { setFilter(next); setPage(0); }} />
    <SpaceResults key={JSON.stringify({ filter, page })} filter={filter} page={page} onPage={setPage} />
  </>;
}
function SpaceResults({ filter, page, onPage }: { filter: SpaceSearchFilters; page: number; onPage: (page: number) => void }) {
  const { data, loading, error, run } = useAsync(() => getSpaces({ page, size: 9, ...toSpaceSearchParams(filter) }), [filter, page]);
  if (loading) return <LoadingSpinner label="조건에 맞는 공간을 찾고 있습니다…" />;
  if (error) return <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />;
  return <>
    {!data?.content.length ? <EmptyState title="조건에 맞는 공간이 없습니다" description="지역·요금·시간 조건을 변경하거나 초기화해 보세요." /> :
      <div className="grid3">{data.content.map((space) => <SpaceCard key={space.id} space={space} availability={space.availability} />)}</div>}
    <Pagination page={data?.page} totalPages={data?.totalPages} totalElements={data?.totalElements} onChange={onPage} />
  </>;
}
