import { useState } from 'react';
import Button from '../common/Button';
import ConfirmDialog from '../common/ConfirmDialog';
import ErrorMessage from '../common/ErrorMessage';

/**
 * 체크아웃은 되돌릴 수 없다 — 실수로 눌렀다면 종료 시각 전까지 키를 재발급받으면 된다
 * (core-domain-decisions.md 8-4). 그래서 확인 다이얼로그를 둔다.
 * 슬롯 반환·환불 없음. 체크아웃을 안 해도 페널티는 없다(방을 끝까지 쓴 것으로 처리).
 */
export default function CheckOutButton({ loading, error, onCheckOut }) {
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <Button wide onClick={() => setConfirming(true)}>
        체크아웃
      </Button>
      <p className="form-help">
        이용을 마쳤다면 체크아웃하세요. 하지 않아도 종료 시각이 지나면 자동으로 처리됩니다.
      </p>

      <ConfirmDialog
        open={confirming}
        title="체크아웃할까요?"
        description="체크아웃하면 되돌릴 수 없습니다. 출입 키도 즉시 폐기됩니다. 슬롯 반환이나 환불은 없습니다."
        confirmLabel="체크아웃"
        confirmVariant="danger"
        loading={loading}
        onClose={() => setConfirming(false)}
        onConfirm={async () => {
          try {
            await onCheckOut();
            setConfirming(false);
          } catch {
            // useAction이 error 상태를 갱신하므로 다이얼로그를 유지해 오류를 보여준다.
          }
        }}
      >
        <ErrorMessage error={error} />
      </ConfirmDialog>
    </>
  );
}
