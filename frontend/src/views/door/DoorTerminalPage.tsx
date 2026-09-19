'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import BrandMark from '../../components/brand/BrandMark';
import { ROUTES } from '../../constants/routePaths';
import { formatDateTime } from '../../utils/date';
import styles from './DoorTerminalPage.module.css';
import type { AccessVerifyResult, ReservationSummary } from '../../types/api';
import type { DoorVerifyFormValue } from '../../components/access/DoorVerifyForm';
import { getMyReservations, getMyReservation } from '../../api/reservationApi';
import { useAuth } from '../../hooks/useAuth';
import { reservationEndTime } from '../../utils/reservationKey';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { verifyAccess } from '../../api/doorAccessApi';
import { useAction, useAsync } from '../../hooks/useApi';
import DoorVerifyForm from '../../components/access/DoorVerifyForm';
import DoorVerifyResult from '../../components/access/DoorVerifyResult';
import AccessCelebration from '../../components/access/AccessCelebration';
import { ACCESS_RESULT } from '../../constants/enums';
import ErrorMessage from '../../components/common/ErrorMessage';

/**
 * 실제 스마트락 대신 사용하는 웹 출입 단말입니다.
 * 허용·거절 판단은 모두 서버가 하며, 이 화면은 결과를 보여주기만 합니다.
 */
export default function DoorTerminalPage() {
  const { member, initializing } = useAuth();
  if (initializing) return <LoadingSpinner />;
  if (!member) return <section className="terminal panel"><h1>모의 출입</h1><p>로그인 후 내 예약으로 출입을 확인할 수 있습니다.</p><Link className="btn primary" href={ROUTES.login}>로그인</Link></section>;
  return <MemberDoorTerminal key={member.memberId} />;
}

const reservationTime = (reservation: ReservationSummary, field: 'startTime' | 'endTime') =>
  reservationEndTime(reservation[field].includes('T') ? reservation[field] : `${reservation.date}T${reservation[field]}`);

function MemberDoorTerminal() {
  const [form, setForm] = useState<DoorVerifyFormValue>({ accessKey: '', reservationId: '' });
  const [result, setResult] = useState<AccessVerifyResult | null>(null);

  const approvedHeading = useRef<HTMLHeadingElement>(null);
  const formHeading = useRef<HTMLHeadingElement>(null);
  const allowed = result?.result === ACCESS_RESULT.ALLOW;
  useEffect(() => {
    if (allowed) approvedHeading.current?.focus();
  }, [allowed]);

  const fetchReservations = useCallback(async () => {
    const groups = await Promise.all((['CONFIRMED', 'IN_USE'] as const).map(async (status) => {
      const rows: ReservationSummary[] = [];
      let page = 0;
      while (true) {
        const response = await getMyReservations({ page, size: 100, status });
        rows.push(...response.content);
        page += 1;
        if (page >= response.totalPages || response.content.length === 0) break;
      }
      return rows;
    }));
    return groups.flat().filter((item) => reservationTime(item, 'endTime') > Date.now())
      .sort((a, b) => reservationTime(a, 'startTime') - reservationTime(b, 'startTime'));
  }, []);
  const { data: reservations, loading: reservationsLoading, error: reservationsError, run: reloadReservations } = useAsync(fetchReservations, [fetchReservations]);

  const { execute, loading, error, setError } = useAction(async () => {
    setResult(null);
    const selected = reservations?.find((item) => String(item.reservationId) === form.reservationId);
    if (!selected) throw new Error('내 예약을 먼저 선택해 주세요.');
    // 이름은 표시용이다. 본인 예약 상세를 재조회하고 공간 ID로만 연결한다.
    const current = await getMyReservation(selected.reservationId);
    if (current.reservationId !== selected.reservationId || current.spaceId !== selected.spaceId) {
      throw new Error('예약 정보가 변경되었습니다. 목록을 새로고침하고 다시 선택해 주세요.');
    }
    if (!['CONFIRMED', 'IN_USE'].includes(current.status)) {
      throw new Error('현재 출입할 수 없는 예약 상태입니다. 내 예약을 확인해 주세요.');
    }
    const response = await verifyAccess({
      token: (form.accessKey ?? '').replace(/\s/g, ''),
      spaceId: current.spaceId,
    });
    setResult(response);
  });

  return (
    <div className={`terminal ${styles.page}`}>
      <div className="terminal-top">
        <p className="page-kicker">ACCESS YOUR SPACE</p>
        <h1 ref={formHeading} tabIndex={-1}>모의 출입</h1>
        <p>{allowed ? '예약한 공간에서 좋은 시간을 시작하세요.' : '내 예약을 선택하고 출입을 확인하세요.'}</p>
      </div>

      {allowed && result ? (
        <section className={styles.approval} aria-labelledby="access-approved-title">
          <div className={styles.brand}><BrandMark size={38} /><span>Slot Key</span></div>
          <div className={styles.check} aria-hidden="true">
            <svg width="42" height="42" viewBox="0 0 40 40" fill="none"><path d="m9 20 7 7L31 12" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </div>
          <h2 id="access-approved-title" ref={approvedHeading} tabIndex={-1}>출입이 허용되었습니다</h2>
          <p className={styles.message}>이제 예약한 공간을 이용하실 수 있습니다</p>
          <dl className={styles.details}>
            {result.spaceName && <div><dt>이용 공간</dt><dd>{result.spaceName}</dd></div>}
            <div><dt>출입 상태</dt><dd>{result.firstCheckIn === true ? '최초 체크인 완료' : result.firstCheckIn === false ? '재입장 승인 완료' : '출입 승인 완료'}</dd></div>
            {result.attemptedAt && <div><dt>승인 시각</dt><dd>{formatDateTime(result.attemptedAt)}</dd></div>}
          </dl>
          <div className={styles.actions}>
            <Link className={styles.primary} href={ROUTES.reservations}>내 예약 보기</Link>
            <button type="button" className={styles.reset} onClick={() => {
              setResult(null);
              setForm({ accessKey: '', reservationId: '' });
              setError(null);
              formHeading.current?.focus();
            }}>다른 출입 확인</button>
          </div>
        </section>
      ) : <>
      {reservationsLoading && <p role="status">내 예약을 불러오고 있습니다…</p>}
      <ErrorMessage error={reservationsError} onRetry={reloadReservations} />
      {!reservationsLoading && !reservationsError && reservations?.length === 0 && <p>출입을 확인할 예약이 없습니다. 확정되었거나 이용 중인 예약만 표시됩니다.</p>}
      <DoorVerifyForm
        reservations={reservations ?? []}
        disabled={reservationsLoading || Boolean(reservationsError) || !reservations?.length}
        value={form}
        onChange={(next) => {
          setForm(next.reservationId !== form.reservationId ? { ...next, accessKey: '' } : next);
          setResult(null);
          setError(null);
        }}
        onSubmit={() => execute().catch(() => {})}
        loading={loading}
      />

      <ErrorMessage error={error} />
      <DoorVerifyResult result={result} />
      </>}
      {result?.result === ACCESS_RESULT.ALLOW && <AccessCelebration />}

      <p className={allowed ? styles.notice : 'note'}>
        {allowed ? '실제 도어락과 연결되지 않는 모의 환경입니다.' : '실제 도어락과 연결되지 않는 모의 환경입니다. 예약자 본인이 예약한 공간과 시간에 이용할 수 있으며, 출입 시도는 기록됩니다.'}
      </p>
    </div>
  );
}
