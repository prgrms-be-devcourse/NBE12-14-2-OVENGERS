import { useEffect, useState } from 'react';
import type { AdminMember } from '../../types/api';
import type { MessageSource } from '../../constants/errorCodes';
import ConfirmDialog from '../common/ConfirmDialog';
import Input from '../common/Input';
import ErrorMessage from '../common/ErrorMessage';
import { MEMBER_STATUS } from '../../constants/enums';

/** 정지·복구에는 사유가 필수이며 감사 로그에 남습니다(FR-MEMBER-08). */
export interface MemberStatusChangeDialogProps {
  /** null 이면 다이얼로그를 띄우지 않습니다. */
  member: AdminMember | null;
  loading?: boolean;
  error: MessageSource | null;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

export default function MemberStatusChangeDialog({
  member,
  loading,
  error,
  onConfirm,
  onClose,
}: MemberStatusChangeDialogProps) {
  const [reason, setReason] = useState('');
  const suspending = member?.status === MEMBER_STATUS.ACTIVE;

  useEffect(() => {
    setReason('');
  }, [member?.memberId]);

  if (!member) return null;

  return (
    <ConfirmDialog
      open
      title={suspending ? '계정을 정지할까요?' : '계정을 복구할까요?'}
      description={
        suspending
          ? '정지하면 이 회원은 보호된 기능을 사용할 수 없습니다. 기존 예약과 결제는 자동으로 취소되지 않습니다.'
          : '복구하면 이 회원은 다시 예약과 출입 기능을 사용할 수 있습니다.'
      }
      confirmLabel={suspending ? '정지' : '복구'}
      confirmVariant={suspending ? 'danger' : 'primary'}
      loading={loading}
      onClose={onClose}
      onConfirm={() => onConfirm(reason.trim())}
    >
      <p className="muted">
        {member.nickname} · {member.email}
      </p>
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
