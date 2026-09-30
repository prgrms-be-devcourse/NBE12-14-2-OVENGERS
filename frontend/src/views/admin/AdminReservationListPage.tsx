'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import type { ReservationFilterValue } from '../../components/reservation/ReservationFilter';
import { getAdminReservations } from '../../api/adminReservationApi';
import { getAllAdminSpaces } from '../../api/adminSpaceApi';
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

export function extractDate(isoString: string): string {
  if (!isoString) return '';
  return isoString.includes('T') ? isoString.split('T')[0] : isoString.slice(0, 10);
}

export function extractTime(isoString: string): string {
  if (!isoString) return '';
  if (isoString.includes('T')) {
    return isoString.split('T')[1].slice(0, 5);
  }
  return isoString.slice(11, 16);
}

export default function AdminReservationListPage() {
  const [filter, setFilter] = useState<ReservationFilterValue>({ date: '', spaceId: '', status: '' });
  const { page, size, setPage } = usePagination({ initialSize: 20 });

  const fetchSpaces = useCallback(() => getAllAdminSpaces(), []);
  const {
    data: spacesData,
    loading: spacesLoading,
    error: spacesError,
    run: runSpaces,
  } = useAsync(fetchSpaces, [fetchSpaces]);

  const fetchReservations = useCallback(
    () => getAdminReservations({ page, size, ...filter }),
    [page, size, filter],
  );
  const { data, loading, error, run } = useAsync(fetchReservations, [fetchReservations]);
  const reservations = data?.content ?? [];
  const hasFilter = Boolean(filter.date || filter.spaceId || filter.status);

  return (
    <>
      <div className="pagehead">
        <div>
          <p className="page-kicker">RESERVATION MANAGEMENT</p>
          <h1>예약 관리</h1>
          <p>날짜와 오피스별 예약 현황을 확인하세요. 취소된 예약도 조회할 수 있습니다.</p>
        </div>
      </div>

      <ReservationFilter
        value={filter}
        showDate
        showSpace
        spaces={spacesData ?? []}
        spacesLoading={spacesLoading}
        spacesError={spacesError}
        onRetrySpaces={runSpaces}
        onChange={(next) => {
          setFilter(next);
          setPage(0);
        }}
      />

      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={run} />

      {!loading && !error && reservations.length === 0 && (
        <EmptyState
          title={hasFilter ? '조건에 맞는 예약이 없습니다' : '등록된 예약이 없습니다'}
          description={hasFilter ? '필터를 조정해 보세요.' : '새로운 예약이 생성되면 여기에 표시됩니다.'}
        />
      )}

      {reservations.length > 0 && (
        <>
          <div className="tablebox" tabIndex={0}>
            <table>
              <caption className="sr-only">전체 예약 목록</caption>
              <thead>
                <tr>
                  <th scope="col">예약 번호</th>
                  <th scope="col">예약자</th>
                  <th scope="col">오피스</th>
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
                      <span>{reservation.memberEmail}</span>
                    </td>
                    <td>{reservation.spaceName}</td>
                    <td>
                      {formatDateLabel(extractDate(reservation.startTime))}
                      <small>{formatTimeRange(extractTime(reservation.startTime), extractTime(reservation.endTime))}</small>
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
