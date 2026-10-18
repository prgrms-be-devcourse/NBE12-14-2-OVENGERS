/**
 * 크레딧 충전 결제의 토스페이먼츠 필수 약관.
 * 토스 약관 위젯(variantKey AGREEMENT)은 상점 설정 defaultAgreementChecked=true 로 체크된 채 그려지고
 * 코드로 바꿀 수 없어서, 같은 약관을 우리 체크박스(초기 미체크)로 받는다.
 * 제목·URL 출처: 토스 약관 위젯이 받아 오는 GET api.tosspayments.com/v1/payment-widget/agreement-widgets 응답(2026-10-18 확인).
 */
export const TOSS_PAYMENT_TERMS = {
  title: '[필수] 결제 서비스 이용 약관, 개인정보 처리 동의',
  items: [
    { title: '서비스 이용약관', url: 'https://pages.tosspayments.com/terms/user' },
    { title: '개인(신용)정보 수집 및 이용 동의', url: 'https://pages.tosspayments.com/terms/privacy/consent1' },
    { title: '개인(신용)정보 제3자 제공 동의', url: 'https://pages.tosspayments.com/terms/privacy/consent2' },
  ],
};
