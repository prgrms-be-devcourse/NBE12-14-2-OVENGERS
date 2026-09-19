import { useState } from 'react';
import Button from '../common/Button';
import ConfirmDialog from '../common/ConfirmDialog';

/**
 * 재발급은 기존 활성 키를 폐기합니다(FR-ACCESS-05).
 * 되돌릴 수 없으므로 재발급일 때만 확인 단계를 둡니다.
 */
export interface AccessKeyIssueButtonProps {
  hasActiveKey: boolean;
  loading?: boolean;
  disabled?: boolean;
  onIssue: () => void | Promise<unknown>;
}

export default function AccessKeyIssueButton({
  hasActiveKey,
  loading,
  disabled,
  onIssue,
}: AccessKeyIssueButtonProps) {
  const [confirming, setConfirming] = useState(false);

  if (!hasActiveKey) {
    return (
      <Button variant="primary" wide loading={loading} disabled={disabled} onClick={onIssue}>
        출입 키 발급
      </Button>
    );
  }

  return (
    <>
      <Button wide disabled={disabled} onClick={() => setConfirming(true)}>
        출입 키 재발급
      </Button>
      <ConfirmDialog
        open={confirming}
        title="출입 키를 재발급할까요?"
        description="재발급하면 기존에 발급받은 키는 즉시 사용할 수 없습니다."
        confirmLabel="재발급"
        confirmVariant="danger"
        loading={loading}
        onClose={() => setConfirming(false)}
        onConfirm={async () => {
          await onIssue();
          setConfirming(false);
        }}
      />
    </>
  );
}
