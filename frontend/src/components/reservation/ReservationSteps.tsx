const steps = ['예약 정보', '결제하기', '예약 완료'];
export default function ReservationSteps({ currentStep }: { currentStep: 1 | 2 | 3 }) {
  return <nav className="booking-progress" aria-label="예약 진행 단계"><ol className="payment-steps">
    {steps.map((label, index) => <li key={label}
      className={index + 1 < currentStep ? 'done' : index + 1 === currentStep ? 'active' : ''}
      aria-current={index + 1 === currentStep ? 'step' : undefined}>
      <span aria-hidden="true">{currentStep === 3 || index + 1 < currentStep ? '✓' : index + 1}</span><b>{label}</b>
    </li>)}
  </ol></nav>;
}
