'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ROUTES } from '@/constants/routePaths';
import { normalizeReturnTo } from '@/utils/creditCharge';
import styles from './ChargePage.module.css';

const FAIL_MESSAGES: Record<string, string> = {
  PAY_PROCESS_CANCELED: '결제가 취소되었습니다.',
  PAY_PROCESS_ABORTED: '결제 인증을 완료하지 못했습니다.',
  REJECT_CARD_COMPANY: '카드사에서 결제를 거절했습니다. 결제수단을 확인해 주세요.',
};

export default function ChargeFailPage() {
  const params = useSearchParams();
  const code = params.get('code') ?? '';
  const returnTo = normalizeReturnTo(params.get('returnTo'));
  const message = FAIL_MESSAGES[code] ?? '결제를 완료하지 못했습니다. 다시 시도해 주세요.';
  const retryHref = `${ROUTES.creditCharge}?returnTo=${encodeURIComponent(returnTo)}`;

  return (
    <main className={styles.result}>
      <p className="page-kicker">PAYMENT NOT COMPLETED</p>
      <h1>크레딧이 충전되지 않았습니다</h1>
      <p role="alert">{message}</p>
      {code && <p className={styles.note}>오류 코드: {code}</p>}
      <div className={styles.resultActions}>
        <Link className="btn primary" href={retryHref}>다시 충전</Link>
        <Link className="btn" href={returnTo}>돌아가기</Link>
      </div>
    </main>
  );
}
