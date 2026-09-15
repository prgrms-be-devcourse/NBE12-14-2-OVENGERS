import { useEffect, useState } from 'react';
import ConfirmDialog from '../common/ConfirmDialog';
import Input from '../common/Input';
import ErrorMessage from '../common/ErrorMessage';

/**
 * 관리자 크레딧 지급(ADMIN_GRANT). 지급만 가능하며 회수는 없다.
 * 사유 1~500자 필수, 자기 자신에게는 지급할 수 없다(core-domain-decisions.md 1-3).
 * 이 다이얼로그는 일반 회원 행에서만 열리므로 자기 자신을 대상으로 선택될 일은 없다.
 */
export default function CreditGrantDialog({ member, loading, error, onConfirm, onClose }) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');

  useEffect(() => {
    setAmount('');
    setReason('');
  }, [member?.memberId]);

  if (!member) return null;

  const amountNumber = Number(amount);
  const valid = amount !== '' && Number.isInteger(amountNumber) && amountNumber > 0 && reason.trim();

  return (
    <ConfirmDialog
      open
      title="크레딧을 지급할까요?"
      description="지급만 가능하며 회수할 수 없습니다. 이 처리는 감사 로그와 크레딧 원장 양쪽에 기록됩니다."
      confirmLabel="지급"
      loading={loading}
      onClose={onClose}
      onConfirm={() => valid && onConfirm({ amount: amountNumber, reason: reason.trim() })}
    >
      <p className="muted">
        {member.nickname} · {member.email}
      </p>
      <Input
        label="지급 크레딧"
        type="number"
        min="1"
        step="1"
        required
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
        help="1크레딧 = 1원 단위입니다."
      />
      <Input
        label="사유"
        required
        maxLength={500}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        help="1~500자. 감사 로그에 기록됩니다."
      />
      <ErrorMessage error={error} />
    </ConfirmDialog>
  );
}
