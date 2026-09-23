'use client';

import { useEffect, useState } from 'react';
import AccessCelebration from '../access/AccessCelebration';
import { consumePaymentCompleted } from '../../utils/paymentCelebration';

export default function PaymentCelebration({ reservationId }: { reservationId: string | number }) {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    let active = true;
    // Strict Mode의 사전 정리에서는 성공 표시를 소모하지 않습니다.
    queueMicrotask(() => {
      if (active && consumePaymentCompleted(reservationId)) setVisible(true);
    });
    return () => { active = false; };
  }, [reservationId]);
  return visible ? <AccessCelebration /> : null;
}
