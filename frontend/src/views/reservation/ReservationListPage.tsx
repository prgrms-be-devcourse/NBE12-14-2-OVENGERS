'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import type { ReservationFilterValue } from '../../components/reservation/ReservationFilter';
import { getMyReservations } from '../../api/reservationApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { ROUTES } from '../../constants/routePaths';
import ReservationCard from '../../components/reservation/ReservationCard';
import ReservationFilter from '../../components/reservation/ReservationFilter';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';

export function isReservationFilterActive(filter: ReservationFilterValue): boolean {
  return Boolean(filter.status) || Boolean(filter.keyword && filter.keyword.trim().length > 0);
}

export default function ReservationListPage() {
  const [filter, setFilter] = useState<ReservationFilterValue>({ status: '', keyword: '' });
  const { page, size, setPage } = usePagination({ initialSize: 10 });

  const fetchReservations = useCallback(
    () => getMyReservations({ page, size, status: filter.status, keyword: filter.keyword }),
    [page, size, filter.status, filter.keyword],
  );
  const { data, loading, error, run } = useAsync(fetchReservations, [fetchReservations]);

  const reservations = data?.content ?? [];
  const hasActiveFilter = isReservationFilterActive(filter);

  return (
    <>
      <div className="pagehead">
        <div>
          <p className="page-kicker">MY RESERVATIONS</p>
          <h1>내 예약</h1>
          <p>다가오는 일정과 지난 이용 내역을 한눈에 확인하세요.</p>
        </div>
        <Link href={ROUTES.spaces} className="btn primary">
          오피스 예약하기
        </Link>
      </div>

      <ReservationFilter
        value={filter}
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
      />

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={run} />

      {!loading && !error && reservations.length === 0 && (
        hasActiveFilter ? (
          <EmptyState
            title="조건에 맞는 예약이 없습니다"
            description="검색어나 예약 상태를 조정해 보세요."
          />
        ) : (
          <EmptyState
            title="아직 예약이 없습니다"
            description="원하는 오피스와 시간을 골라 첫 예약을 만들어 보세요."
            action={
              <Link href={ROUTES.spaces} className="btn primary">
                오피스 둘러보기
              </Link>
            }
          />
        )
      )}

      {reservations.map((reservation) => (
        <ReservationCard key={reservation.reservationId} reservation={reservation} />
      ))}

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onChange={setPage}
        />
      )}
    </>
  );
}
