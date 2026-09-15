import { formatCredit, formatWon } from '../../utils/price';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';

/**
 * 결제 확인 단계(POST /reservations/{id}/pay). HOLD 생성과는 별도 요청이며,
 * Mock 결제 서비스가 크레딧 잔액을 차감한다. 실패 조건은 잔액 부족 하나뿐이다
 * (core-domain-decisions.md 1-1). 실제 카드 정보는 입력받지 않는다.
 */
export default function MockPaymentSection({ amount, balance, onSubmit, submitting, error, disabled }) {
  const insufficient = typeof balance === 'number' && balance < amount;

  return (
    <>
      <div className="definition">
        <span>내 크레딧 잔액</span>
        <strong>{typeof balance === 'number' ? formatCredit(balance) : '-'}</strong>
      </div>

      {insufficient && (
        <p className="form-help" style={{ color: 'var(--red)' }}>
          잔액이 결제 금액보다 부족합니다. 예약은 결제 대기 상태로 유지되며, 만료 전까지 다시
          시도할 수 있습니다.
        </p>
      )}

      <p className="note">
        실제 결제가 아닌 모의 결제입니다. 카드 정보를 입력받지 않으며 크레딧 잔액만 차감됩니다.
      </p>

      <ErrorMessage error={error} />

      <Button
        variant="primary"
        wide
        loading={submitting}
        disabled={disabled || insufficient}
        onClick={onSubmit}
      >
        {formatWon(amount)} 결제하기
      </Button>
    </>
  );
}
