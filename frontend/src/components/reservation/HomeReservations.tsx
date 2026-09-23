'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { getMyReservations } from '../../api/reservationApi';
import { useAuth } from '../../hooks/useAuth';
import { useAsync } from '../../hooks/useApi';
import { RESERVATION_STATUS_META } from '../../constants/enums';
import { ROUTES } from '../../constants/routePaths';
import type { ReservationSummary } from '../../types/api';
import Badge from '../common/Badge';
import { formatDateShort } from '../../utils/date';
import { HOME_RESERVATION_STATUSES, selectHomeReservations } from '../../utils/homeReservations';
import styles from './HomeReservations.module.css';

async function fetchActiveReservations() {
  // The server sorts by start time descending. Read every active page before
  // selecting the nearest bookings, so older/upcoming bookings aren't omitted.
  const groups = await Promise.all(HOME_RESERVATION_STATUSES.map(async (status) => {
    const first = await getMyReservations({ status, size: 50 });
    const rows = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
      rows.push(...(await getMyReservations({ status, size: 50, page })).content);
    }
    return rows;
  }));
  return groups.flat();
}

export default function HomeReservations() {
  const { member, isAdmin } = useAuth();
  if (!member || isAdmin) return null;
  return <MemberReservations key={member.memberId} />;
}

function MemberReservations() {
  const { data, loading, error, run } = useAsync(fetchActiveReservations);
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') void run().catch(() => {});
    };
    const timer = window.setInterval(refresh, 30000);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [run]);

  const reservations = selectHomeReservations(data ?? []);
  if (!loading && !error && reservations.length === 0) return null;
  return <section className={styles.summary} aria-labelledby="home-reservations-title">
    <div className={styles.heading}>
      <h2 id="home-reservations-title">진행 중인 예약</h2>
      <Link href={ROUTES.reservations}>내 예약 전체 보기 <Chevron /></Link>
    </div>
    {error ? <div className={styles.message} role="alert">
      <span>예약 정보를 불러오지 못했습니다.</span>
      <button type="button" disabled={loading} onClick={() => { void run().catch(() => {}); }}>다시 불러오기</button>
    </div> : !data ? <p className={styles.message} role="status">진행 중인 예약을 확인하고 있습니다.</p>
      : <ReservationPreview reservations={reservations} />}
  </section>;
}

function Chevron() {
  return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>;
}

export function ReservationPreview({ reservations }: { reservations: ReservationSummary[] }) {
  return <div className={styles.cards}>
    {reservations.map((reservation) => {
      const pending = reservation.status === 'HELD';
      const upcoming = reservation.status === 'CONFIRMED';
      const label = pending ? '결제 대기' : upcoming ? '예약 완료' : '사용 중';
      const action = pending ? '결제하기' : upcoming ? '체크인 예정 · 상세 보기' : '이용 중 · 상세 보기';
      const href = pending ? ROUTES.reservationPayment(reservation.reservationId) : ROUTES.reservationDetail(reservation.reservationId);
      const time = (value: string) => value.includes('T') ? value.split('T')[1].slice(0, 5) : value.slice(0, 5);
      return <Link key={reservation.reservationId} href={href} className={styles.card}>
        <div className={styles.cardHeading}>
          <Badge tone={RESERVATION_STATUS_META[reservation.status].tone}>{label}</Badge>
          <Chevron />
        </div>
        <h3>{reservation.spaceName}</h3>
        <p>{formatDateShort(reservation.date)} · {time(reservation.startTime)}–{time(reservation.endTime)}</p>
        <span className={styles.action}>{action}</span>
      </Link>;
    })}
  </div>;
}
