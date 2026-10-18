'use client';

import Link from 'next/link';
import { useCallback } from 'react';
import { getAdminMemberReservations } from '../../api/adminMemberApi';
import { useAsync } from '../../hooks/useApi';
import { usePagination } from '../../hooks/usePagination';
import { ROUTES } from '../../constants/routePaths';
import ReservationStatusBadge from '../../components/reservation/ReservationStatusBadge';
import Pagination from '../../components/common/Pagination';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import { formatDateTime } from '../../utils/date';
import { formatWon } from '../../utils/price';
import styles from './AdminMemberDetailPage.module.css';

export default function AdminMemberReservations({
                                                    memberId,
                                                }: {
    memberId: number;
}) {
    const { page, size, setPage } = usePagination({
        initialSize: 20,
    });

    const fetchReservations = useCallback(
        () => getAdminMemberReservations(memberId, page, size),
        [memberId, page, size],
    );

    const { data, loading, error, run } = useAsync(
        fetchReservations,
        [fetchReservations],
    );

    const reservations = data?.content ?? [];

    return (
        <div className={styles.tabPanel}>
            <div className={styles.tabHeading}>
                <h3 className={styles.tabTitle}>예약 내역</h3>
                <p className={styles.tabDescription}>
                    해당 회원의 모든 예약을 최신순으로 조회합니다.
                    취소 및 만료된 예약도 포함됩니다.
                </p>
            </div>

            {loading && (
                <LoadingSpinner label="예약 내역을 불러오는 중입니다…" />
            )}

            <ErrorMessage
                error={error}
                onRetry={() => {
                    void run().catch(() => {});
                }}
            />

            {!loading && !error && data && reservations.length === 0 && (
                <EmptyState
                    title="예약 내역이 없습니다"
                    description="아직 생성한 예약이 없습니다."
                />
            )}

            {!loading && !error && reservations.length > 0 && (
                <div className="tablebox" tabIndex={0}>
                    <table>
                        <caption className="sr-only">
                            회원 예약 내역
                        </caption>
                        <thead>
                        <tr>
                            <th scope="col">예약 번호</th>
                            <th scope="col">오피스</th>
                            <th scope="col">이용 시작</th>
                            <th scope="col">이용 종료</th>
                            <th scope="col" className={styles.alignRight}>
                                금액
                            </th>
                            <th scope="col">상태</th>
                            <th scope="col">상세</th>
                        </tr>
                        </thead>
                        <tbody>
                        {reservations.map((reservation) => (
                            <tr key={reservation.reservationId}>
                                <td>#{reservation.reservationId}</td>
                                <td>{reservation.spaceName}</td>
                                <td>
                                    {formatDateTime(
                                        reservation.startTime,
                                    ) || '-'}
                                </td>
                                <td>
                                    {formatDateTime(
                                        reservation.endTime,
                                    ) || '-'}
                                </td>
                                <td className={styles.alignRight}>
                                    {formatWon(reservation.totalAmount)}
                                </td>
                                <td>
                                    <ReservationStatusBadge
                                        status={reservation.status}
                                    />
                                </td>
                                <td>
                                    <Link
                                        href={ROUTES.adminReservationDetail(
                                            reservation.reservationId,
                                        )}
                                    >
                                        보기
                                    </Link>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {!loading && !error && data && (
                <Pagination
                    page={data.page}
                    totalPages={data.totalPages}
                    totalElements={data.totalElements}
                    onChange={setPage}
                />
            )}
        </div>
    );
}
