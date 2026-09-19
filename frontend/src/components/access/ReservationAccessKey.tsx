'use client';

import { useEffect, useState } from 'react';
import type { IssuedDoorToken, ReservationStatus } from '../../types/api';
import { ensureReservationKey, forgetReservationKey, reservationEndTime, reservationKeyStorageId } from '../../utils/reservationKey';
import AccessKeyPanel from './AccessKeyPanel';
import Button from '../common/Button';
import styles from './ReservationAccessKey.module.css';

export default function ReservationAccessKey({ memberId, reservationId, status, startTime, endTime }: {
  memberId: number;
  reservationId: number;
  status: ReservationStatus;
  startTime: string;
  endTime: string;
}) {
  const [key, setKey] = useState<IssuedDoorToken | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [revealed, setRevealed] = useState(false);
  const [clockReady, setClockReady] = useState(false);
  const [saved, setSaved] = useState(true);
  const active = (status === 'CONFIRMED' || status === 'IN_USE') && now < reservationEndTime(endTime);

  const opensAt = reservationEndTime(startTime) - 15 * 60 * 1000;
  const locked = !clockReady || !Number.isFinite(opensAt) || now < opensAt;
  const visibleId = `${reservationKeyStorageId(memberId, reservationId)}:visible`;

  useEffect(() => {
    try { setRevealed(window.localStorage.getItem(visibleId) === '1'); } catch { /* 보관 실패 시 확인 버튼을 표시한다. */ }
    setClockReady(true);
    const update = () => setNow(Date.now());
    update();
    const timer = window.setInterval(update, 1000);
    window.addEventListener('focus', update);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', update); };
  }, [visibleId]);

  useEffect(() => {
    let cancelled = false;
    if (!active) {
      forgetReservationKey(memberId, reservationId);
      setKey(null);
      return;
    }
    setError(null);
    ensureReservationKey(memberId, reservationId, endTime).then((value) => {
      if (cancelled) return;
      setKey(value);
      try { setSaved(Boolean(window.localStorage.getItem(reservationKeyStorageId(memberId, reservationId)))); }
      catch { setSaved(false); }
    }).catch((caught: unknown) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : '출입 키를 준비하지 못했습니다.');
    });
    return () => { cancelled = true; };
  }, [memberId, reservationId, active, endTime, attempt]);

  if (!active) return <section className="key-card"><h2>출입 키</h2><p>예약이 취소되거나 이용이 종료되어 출입 키를 사용할 수 없습니다.</p></section>;
  if (locked) {
    const seconds = Math.max(0, Math.ceil((opensAt - now) / 1000));
    const countdown = !clockReady || !Number.isFinite(seconds) ? '시간 확인 중…' : [Math.floor(seconds / 3600), Math.floor(seconds % 3600 / 60), seconds % 60].map((value) => String(value).padStart(2, '0')).join(':');
    return <section className={`key-card ${styles.locked}`}>
      <h2>출입 키</h2>
      <p>출입 키는 예약 시작 15분 전부터 확인할 수 있습니다.</p>
      <button className={styles.countdown} type="button" disabled aria-label={`출입 키 확인까지 ${countdown}`}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="3" stroke="currentColor" strokeWidth="1.7" /><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" strokeWidth="1.7" /></svg>
        <span>{countdown}</span>
      </button>
    </section>;
  }
  if (!key) return <section className="key-card">
    <h2>출입 키</h2>
    {error ? <><p role="alert">{error}</p><Button onClick={() => setAttempt((value) => value + 1)}>다시 확인</Button></> : <p role="status">출입 키를 준비하고 있습니다…</p>}
  </section>;
  if (!revealed) return <section className="key-card">
    <h2>출입 키가 준비되었습니다</h2>
    <p>예약 시작 시간부터 이용할 수 있습니다.</p>
    <Button variant="primary" wide onClick={() => {
      setRevealed(true);
      try { window.localStorage.setItem(visibleId, '1'); } catch { /* 현재 화면에서는 확인 가능 */ }
    }}>출입 키 확인</Button>
  </section>;
  return <AccessKeyPanel accessKey={key.token} issuedAt={key.issuedAt}
    description={saved ? '같은 브라우저에서는 다시 방문해도 이 키를 확인할 수 있습니다.' : '브라우저에 저장하지 못했습니다. 지금 키를 복사해 보관해 주세요.'}
    notice="예약 시작 시간부터 이용할 수 있습니다. 예약 취소 또는 이용 종료 시 키는 사용할 수 없습니다." />;
}
