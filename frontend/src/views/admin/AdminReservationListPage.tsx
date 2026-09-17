'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import type { ReservationFilterValue } from '../../components/reservation/ReservationFilter';
import { getAdminReservations } from '../../api/adminReservationApi';
import { getSpaces } from '../../api/spaceApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { ROUTES } from '../../constants/routePaths';
import { formatDateLabel, formatTimeRange } from '../../utils/date';
import { formatWon } from '../../utils/price';
import { formatReservationNo } from '../../utils/format';
import ReservationStatusBadge from '../../components/reservation/ReservationStatusBadge';
import ReservationFilter from '../../components/reservation/ReservationFilter';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';

export default function AdminReservationListPage() {
  const [filter, setFilter] = useState<ReservationFilterValue>({ date: '', spaceId: '', status: '' });
  const { page, size, setPage } = usePagination({ initialSize: 20 });

  const fetchSpaces = useCallback(() => getSpaces({ size: 100 }), []);
  const { data: spaceData } = useAsync(fetchSpaces, [fetchSpaces]);

  const fetchReservations = useCallback(
    () => getAdminReservations({ page, size, ...filter }),
    [page, size, filter],
  );
  const { data, loading, error, run } = useAsync(fetchReservations, [fetchReservations]);
  const reservations = data?.content ?? [];

  return (
    <>
      <div className="pagehead">
        <div>
          <h1>예약 관리</h1>
          <p>취소된 예약도 삭제하지 않고 그대로 조회할 수 있습니다.</p>
        </div>
      </div>

      <ReservationFilter
        value={filter}
        showDate
        showSpace
        spaces={spaceData?.content ?? []}
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
      />

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={run} />

      {!loading && !error && reservations.length === 0 && (
        <EmptyState title="조건에 맞는 예약이 없습니다" description="필터를 조정해 보세요." />
      )}

      {reservations.length > 0 && (
        <>
          <div className="tablebox">
            <table>
              <caption className="sr-only">전체 예약 목록</caption>
              <thead>
                <tr>
                  <th scope="col">예약 번호</th>
                  <th scope="col">예약자</th>
                  <th scope="col">공간</th>
                  <th scope="col">이용 일시</th>
                  <th scope="col">금액</th>
                  <th scope="col">상태</th>
                  <th scope="col">상세</th>
                </tr>
              </thead>
              <tbody>
                {reservations.map((reservation) => (
                  <tr key={reservation.reservationId}>
                    <td>{formatReservationNo(reservation.reservationId)}</td>
                    <td>
                      {reservation.memberNickname}
                      <small>{reservation.memberEmail}</small>
                    </td>
                    <td>{reservation.spaceName}</td>
                    <td>
                      {formatDateLabel(reservation.date)}
                      <small>{formatTimeRange(reservation.startTime, reservation.endTime)}</small>
                    </td>
                    <td>{formatWon(reservation.totalAmount)}</td>
                    <td>
                      <ReservationStatusBadge status={reservation.status} />
                    </td>
                    <td>
                      <Link href={ROUTES.adminReservationDetail(reservation.reservationId)}>
                        보기
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
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
