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
