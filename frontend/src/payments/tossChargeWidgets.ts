import {
  ANONYMOUS,
  loadTossPayments,
} from '@tosspayments/tosspayments-sdk';
import type { WidgetPaymentMethodWidget } from '@tosspayments/tosspayments-sdk';

export interface ChargePaymentRequest {
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
  customerEmail?: string;
  customerName?: string;
}
export interface ChargeWidgetsSession {
  requestPayment: (request: ChargePaymentRequest) => Promise<void>;
  destroy: () => Promise<void>;
}

const CLIENT_KEY_PATTERN = /^(?:test|live)_gck_/;

export function getTossClientKeyError(
  clientKey: string | undefined,
): string | null {
  if (!clientKey) return '토스페이먼츠 클라이언트 키가 설정되지 않았습니다.';
  if (!CLIENT_KEY_PATTERN.test(clientKey)) {
    return '토스페이먼츠 클라이언트 키 형식이 아닙니다. 시크릿 키를 넣지 않았는지 확인해 주세요.';
  }
  return null;
}

export async function setupChargeWidgets({
  clientKey,
  amount,
}: {
  clientKey: string;
  amount: number;
}): Promise<ChargeWidgetsSession> {
  const clientKeyError = getTossClientKeyError(clientKey);
  if (clientKeyError) throw new Error(clientKeyError);

  const tossPayments = await loadTossPayments(clientKey);
  const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });

  await widgets.setAmount({ currency: 'KRW', value: amount });
  const paymentMethods: WidgetPaymentMethodWidget =
    await widgets.renderPaymentMethods({
      selector: '#payment-method',
      variantKey: 'DEFAULT',
    });
  // 약관 위젯(renderAgreement)은 쓰지 않는다. 상점 설정으로 체크된 채 그려져 사용자가 직접 동의할 수 없기 때문이다.
  // 필수 약관 동의는 충전 화면의 체크박스(TOSS_PAYMENT_TERMS)로 받는다.

  return {
    requestPayment: async (request) => {
      await widgets.requestPayment(request);
    },
    destroy: async () => {
      await paymentMethods.destroy();
    },
  };
}
