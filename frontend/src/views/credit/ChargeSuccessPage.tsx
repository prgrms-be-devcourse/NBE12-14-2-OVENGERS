'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { confirmChargeOrder, getChargeOrder } from '@/api/creditApi';
import Button from '@/components/common/Button';
import ErrorMessage from '@/components/common/ErrorMessage';
import LoadingSpinner from '@/components/common/LoadingSpinner';
import { ROUTES } from '@/constants/routePaths';
import { useAuth } from '@/hooks/useAuth';
import { useIdempotencyKey } from '@/hooks/useIdempotencyKey';
import type { ApiError } from '@/api/client';
import type { ChargeOrderStatus } from '@/types/api';
import {
  normalizeReturnTo,
  PollCancelledError,
  pollChargeOrder,
} from '@/utils/creditCharge';
import { formatWon } from '@/utils/price';
import styles from './ChargePage.module.css';

type ResultState = 'APPROVING' | ChargeOrderStatus | 'ERROR';

export default function ChargeSuccessPage() {
  const params = useSearchParams();
  const paymentKey = params.get('paymentKey');
  const orderId = params.get('orderId');
  const amount = Number(params.get('amount'));
  const returnTo = normalizeReturnTo(params.get('returnTo'));
  const { refreshMember } = useAuth();
  const { renew } = useIdempotencyKey();
  const [state, setState] = useState<ResultState>('APPROVING');
  const [error, setError] = useState<ApiError | null>(null);
  const [approvalAttempt, setApprovalAttempt] = useState(0);
  const startedAttempt = useRef(-1);
  const activeRef = useRef(false);

  const finish = useCallback(async (status: ChargeOrderStatus) => {
    setState(status);
    if (status === 'PAID') await refreshMember().catch(() => {});
  }, [refreshMember]);

  useEffect(() => {
    activeRef.current = true;
    if (startedAttempt.current === approvalAttempt) {
      return () => { activeRef.current = false; };
    }
    startedAttempt.current = approvalAttempt;

    async function approve() {
      if (!paymentKey || !orderId || !Number.isInteger(amount) || amount <= 0) {
        setState('ERROR');
        return;
      }
      try {
        const confirmed = await confirmChargeOrder(
          { paymentKey, orderId, amount },
          renew(),
        );
        if (!activeRef.current) return;
        if (confirmed.status !== 'CONFIRMING') {
          await finish(confirmed.status);
          return;
        }
        setState('CONFIRMING');
        const recovered = await pollChargeOrder({
          getOrder: () => getChargeOrder(orderId),
          wait: () => new Promise((resolve) => window.setTimeout(resolve, 3_000)),
          maxAttempts: 40,
          isActive: () => activeRef.current,
        });
        if (activeRef.current) await finish(recovered.status);
      } catch (caught) {
        if (!activeRef.current || caught instanceof PollCancelledError) return;
        setError(caught as ApiError);
        setState('ERROR');
      }
    }

    void approve();
    return () => { activeRef.current = false; };
  }, [amount, approvalAttempt, finish, orderId, paymentKey, renew]);

  const recheck = async () => {
    if (!orderId) return;
    try {
      setError(null);
      const order = await getChargeOrder(orderId);
      await finish(order.status);
    } catch (caught) {
      setError(caught as ApiError);
      setState('ERROR');
    }
  };

  const retryApproval = () => {
    setError(null);
    setState('APPROVING');
    setApprovalAttempt((current) => current + 1);
  };

  if (state === 'APPROVING') return <LoadingSpinner label="결제를 승인하고 있습니다…" />;
  if (state === 'CONFIRMING') {
    return (
      <main className={styles.result}>
        <h1>충전을 처리하고 있습니다</h1>
        <p>결제 결과를 확인 중입니다. 이 화면을 닫아도 서버에서 계속 확인합니다.</p>
        <Button onClick={() => void recheck()}>다시 확인</Button>
      </main>
    );
  }
  if (state === 'PAID') {
    return (
      <main className={styles.result}>
        <p className="page-kicker">PAYMENT COMPLETE</p>
        <h1>크레딧 충전이 완료되었습니다</h1>
        <p><strong>{formatWon(amount)}</strong>이 충전되었습니다.</p>
        <div className={styles.resultActions}>
          <Link className="btn primary" href={returnTo}>돌아가기</Link>
        </div>
      </main>
    );
  }
  if (state === 'FAILED') {
    return (
      <main className={styles.result}>
        <h1>결제를 완료하지 못했습니다</h1>
        <p>크레딧은 충전되지 않았습니다. 새 주문으로 다시 시도해 주세요.</p>
        <div className={styles.resultActions}>
          <Link className="btn primary" href={`${ROUTES.creditCharge}?returnTo=${encodeURIComponent(returnTo)}`}>다시 충전</Link>
          <Link className="btn" href={returnTo}>돌아가기</Link>
        </div>
      </main>
    );
  }
  return (
    <main className={styles.result}>
      <h1>결제 결과를 확인하지 못했습니다</h1>
      {!paymentKey || !orderId || !Number.isInteger(amount) || amount <= 0 ? (
        <p role="alert">결제 결과 정보가 올바르지 않습니다. 충전 화면에서 다시 시도해 주세요.</p>
      ) : (
        <ErrorMessage error={error} fallback="결제 결과 확인 중 오류가 발생했습니다." onRetry={retryApproval} />
      )}
      <div className={styles.resultActions}>
        <Link className="btn" href={returnTo}>돌아가기</Link>
      </div>
    </main>
  );
}
