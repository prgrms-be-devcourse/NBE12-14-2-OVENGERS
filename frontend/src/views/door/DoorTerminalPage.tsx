'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import BrandMark from '../../components/brand/BrandMark';
import { ROUTES } from '../../constants/routePaths';
import { formatDateTime } from '../../utils/date';
import styles from './DoorTerminalPage.module.css';
import type { AccessVerifyResult } from '../../types/api';
import type { DoorVerifyFormValue } from '../../components/access/DoorVerifyForm';
import { getSpaces } from '../../api/spaceApi';
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
  const [form, setForm] = useState<DoorVerifyFormValue>({ accessKey: '', spaceId: '' });
  const [result, setResult] = useState<AccessVerifyResult | null>(null);

  const approvedHeading = useRef<HTMLHeadingElement>(null);
  const formHeading = useRef<HTMLHeadingElement>(null);
  const allowed = result?.result === ACCESS_RESULT.ALLOW;
  useEffect(() => {
    if (allowed) approvedHeading.current?.focus();
  }, [allowed]);

  const fetchSpaces = useCallback(() => getSpaces({ size: 100 }), []);
  const { data: spaceData } = useAsync(fetchSpaces, [fetchSpaces]);

  const { execute, loading, error, setError } = useAction(async () => {
    setResult(null);
    const response = await verifyAccess({
      token: form.accessKey ?? '',
      spaceId: Number(form.spaceId),
    });
    setResult(response);
  });

  return (
    <div className={`terminal ${styles.page}`}>
      <div className="terminal-top">
        <p className="page-kicker">ACCESS YOUR SPACE</p>
        <h1 ref={formHeading} tabIndex={-1}>모의 출입</h1>
        <p>{allowed ? '예약한 공간에서 좋은 시간을 시작하세요.' : '예약한 공간과 출입 키를 입력해 이용 가능 여부를 확인하세요.'}</p>
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
              setForm({ accessKey: '', spaceId: '' });
              setError(null);
              formHeading.current?.focus();
            }}>다른 출입 확인</button>
          </div>
        </section>
      ) : <>
      <DoorVerifyForm
        spaces={spaceData?.content ?? []}
        value={form}
        onChange={(next) => {
          setForm(next);
          setResult(null);
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
