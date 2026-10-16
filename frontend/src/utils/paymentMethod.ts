/**
 * 토스페이먼츠가 승인 응답으로 알려 주는 결제수단(method)과 간편결제 제공사(provider)를
 * 화면용 아이콘·문구로 바꾼다. 서버 값은 표시용이라 모르는 값은 그대로 보여 주고, 없으면 null을 돌려준다.
 */

export interface PaymentMethodView {
  icon: string;
  label: string;
}

const METHOD_ICONS: Record<string, string> = {
  카드: '💳',
  간편결제: '📱',
  계좌이체: '🏦',
  가상계좌: '🧾',
  휴대폰: '📞',
  문화상품권: '🎁',
  도서문화상품권: '🎁',
  게임문화상품권: '🎁',
};

const FALLBACK_ICON = '💰';

export function getPaymentMethodView(
  method: string | null | undefined,
  provider?: string | null,
): PaymentMethodView | null {
  const name = method?.trim();
  if (!name) return null;
  const icon = METHOD_ICONS[name] ?? FALLBACK_ICON;
  const detail = provider?.trim();
  // 간편결제는 "간편결제 · 토스페이"처럼 제공사를 함께 보여 준다.
  return { icon, label: detail ? `${name} · ${detail}` : name };
}
