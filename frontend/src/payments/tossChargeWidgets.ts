import {
  ANONYMOUS,
  loadTossPayments,
} from '@tosspayments/tosspayments-sdk';
import type {
  WidgetAgreementWidget,
  WidgetPaymentMethodWidget,
} from '@tosspayments/tosspayments-sdk';

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
  onAgreementChange,
}: {
  clientKey: string;
  amount: number;
  onAgreementChange: (agreed: boolean) => void;
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
  let agreement: WidgetAgreementWidget;
  try {
    agreement = await widgets.renderAgreement({
      selector: '#agreement',
      variantKey: 'AGREEMENT',
    });
  } catch (error) {
    await paymentMethods.destroy();
    throw error;
  }
  agreement.on('agreementStatusChange', ({ agreedRequiredTerms }) => {
    onAgreementChange(agreedRequiredTerms);
  });
  // 토스 약관 위젯은 기본으로 전체 동의 상태로 그려지지만 사용자가 바꿀 때만 이벤트를 보낸다.
  // 최초 상태를 알려 주지 않으면 이미 체크돼 있어도 결제 버튼이 비활성으로 남는다.
  // 동의 여부는 requestPayment 에서 토스가 한 번 더 검증한다.
  onAgreementChange(true);

  return {
    requestPayment: async (request) => {
      await widgets.requestPayment(request);
    },
    destroy: async () => {
      await paymentMethods.destroy();
      await agreement.destroy();
    },
  };
}
