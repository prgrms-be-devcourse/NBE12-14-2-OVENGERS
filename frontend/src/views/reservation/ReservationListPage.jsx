'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
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

export default function ReservationListPage() {
  const [filter, setFilter] = useState({ status: '' });
  const { page, size, setPage } = usePagination({ initialSize: 10 });

  const fetchReservations = useCallback(
    () => getMyReservations({ page, size, status: filter.status }),
    [page, size, filter.status],
  );
  const { data, loading, error, run } = useAsync(fetchReservations, [fetchReservations]);

  const reservations = data?.content ?? [];

  return (
    <>
      <div className="pagehead">
        <div>
          <h1>내 예약</h1>
          <p>본인이 만든 예약만 보입니다. 취소한 예약도 기록으로 남습니다.</p>
        </div>
        <Link href={ROUTES.spaces} className="btn primary">
          공간 예약하기
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
        <EmptyState
          title="아직 예약이 없습니다"
          description="원하는 공간과 시간을 골라 첫 예약을 만들어 보세요."
          action={
            <Link href={ROUTES.spaces} className="btn primary">
              공간 둘러보기
            </Link>
          }
        />
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
