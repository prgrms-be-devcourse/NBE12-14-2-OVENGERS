'use client';

import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { createChargeOrder } from '@/api/creditApi';
import Button from '@/components/common/Button';
import ErrorMessage from '@/components/common/ErrorMessage';
import { ROUTES } from '@/constants/routePaths';
import { TOSS_PAYMENT_TERMS } from '@/constants/tossPaymentTerms';
import { useAction } from '@/hooks/useApi';
import { useAuth } from '@/hooks/useAuth';
import {
  getTossClientKeyError,
  setupChargeWidgets,
} from '@/payments/tossChargeWidgets';
import type { ChargeWidgetsSession } from '@/payments/tossChargeWidgets';
import type { ChargeOrder } from '@/types/api';
import {
  buildChargeRedirectUrl,
  normalizeReturnTo,
  suggestChargeAmount,
} from '@/utils/creditCharge';
import { formatWon } from '@/utils/price';
import styles from './ChargePage.module.css';

const QUICK_AMOUNTS = [1_000, 5_000, 10_000, 30_000, 50_000, 100_000];

export default function ChargePage() {
  const searchParams = useSearchParams();
  const { member } = useAuth();
  const clientKey = process.env.NEXT_PUBLIC_TOSS_PAYMENTS_CLIENT_KEY;
  const clientKeyError = getTossClientKeyError(clientKey);
  const returnTo = normalizeReturnTo(searchParams.get('returnTo'));
  const requestedAmount = Number(searchParams.get('amount'));
  const initialAmount = Number.isFinite(requestedAmount) && requestedAmount > 0
    ? suggestChargeAmount(requestedAmount, 0)
    : 1_000;
  const [amount, setAmount] = useState(initialAmount);
  const [order, setOrder] = useState<ChargeOrder | null>(null);
  const [widgetReady, setWidgetReady] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const sessionRef = useRef<ChargeWidgetsSession | null>(null);

  useEffect(() => () => {
    void sessionRef.current?.destroy().catch(() => {});
    sessionRef.current = null;
  }, []);

  const prepare = useAction(async () => {
    if (clientKeyError || !clientKey) {
      throw new Error(clientKeyError ?? '토스페이먼츠 클라이언트 키 설정을 확인해 주세요.');
    }

    const created = await createChargeOrder(amount);
    setOrder(created);
    setWidgetReady(false);
    setAgreed(false);
    try {
      const session = await setupChargeWidgets({
        clientKey,
        amount: created.amount,
      });
      sessionRef.current = session;
      setWidgetReady(true);
      return created;
    } catch (error) {
      setOrder(null);
      throw error;
    }
  });

  const pay = useAction(async () => {
    if (!order || !sessionRef.current) return;
    await sessionRef.current.requestPayment({
      orderId: order.orderId,
      orderName: `Slot Key 크레딧 ${order.amount.toLocaleString('ko-KR')}원 충전`,
      successUrl: buildChargeRedirectUrl(
        window.location.origin,
        ROUTES.creditChargeSuccess,
        returnTo,
      ),
      failUrl: buildChargeRedirectUrl(
        window.location.origin,
        ROUTES.creditChargeFail,
        returnTo,
      ),
      customerEmail: member?.email,
      customerName: member?.nickname,
    });
  });

  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <p className="page-kicker">CREDIT TOP UP</p>
        <h1>크레딧 충전</h1>
        <p>충전할 금액과 결제수단을 확인한 뒤 결제를 진행해 주세요.</p>
      </header>

      <div className={styles.grid}>
        <section className={styles.panel} aria-labelledby="charge-amount-title">
          <h2 id="charge-amount-title">충전 금액</h2>
          <label className={styles.amountInput}>
            <span>금액</span>
            <input
              type="number"
              min={1_000}
              max={100_000}
              step={100}
              value={amount}
              disabled={Boolean(order) || prepare.loading}
              onChange={(event) => setAmount(Number(event.target.value))}
            />
          </label>
          <div className={styles.quickAmounts} aria-label="빠른 금액 선택">
            {QUICK_AMOUNTS.map((value) => (
              <Button
                key={value}
                size="small"
                disabled={Boolean(order) || prepare.loading}
                onClick={() => setAmount(value)}
              >
                {formatWon(value)}
              </Button>
            ))}
          </div>
          {!order && (
            <Button
              variant="primary"
              wide
              loading={prepare.loading}
              disabled={Boolean(clientKeyError)}
              onClick={() => void prepare.execute().catch(() => {})}
            >
              결제수단 불러오기
            </Button>
          )}
          <ErrorMessage error={clientKeyError} />
          <ErrorMessage error={prepare.error} />

          {order && (
            <div className={styles.widgetArea}>
              <div id="payment-method" aria-label="결제수단" />
              <div className={styles.agreement}>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={agreed}
                    disabled={!widgetReady}
                    onChange={(event) => setAgreed(event.target.checked)}
                  />
                  <span>{TOSS_PAYMENT_TERMS.title}</span>
                </label>
                <ul>
                  {TOSS_PAYMENT_TERMS.items.map((term) => (
                    <li key={term.url}>
                      <a href={term.url} target="_blank" rel="noopener noreferrer">{term.title} 보기</a>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}
        </section>

        <aside className={`${styles.panel} ${styles.summary}`}>
          <h2>충전 요약</h2>
          <dl>
            <div><dt>현재 크레딧</dt><dd>{formatWon(member?.balance ?? 0)}</dd></div>
            <div><dt>충전 금액</dt><dd>{formatWon(order?.amount ?? amount)}</dd></div>
            <div className={styles.summaryTotal}>
              <dt>충전 후 예상</dt>
              <dd>{formatWon((member?.balance ?? 0) + (order?.amount ?? amount))}</dd>
            </div>
          </dl>
          <p className={styles.note}>결제 금액은 서버가 생성한 주문 금액으로 확정됩니다.</p>
          <div className={styles.actions}>
            <Button
              variant="primary"
              wide
              loading={pay.loading}
              disabled={!widgetReady || !agreed}
              onClick={() => void pay.execute().catch(() => {})}
            >
              {formatWon(order?.amount ?? amount)} 결제하기
            </Button>
          </div>
          <ErrorMessage error={pay.error} />
        </aside>
      </div>
    </main>
  );
}
