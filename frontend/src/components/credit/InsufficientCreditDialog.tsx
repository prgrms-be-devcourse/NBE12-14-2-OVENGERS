'use client';

import { useRouter } from 'next/navigation';
import { ROUTES } from '../../constants/routePaths';
import { formatWon } from '../../utils/price';
import Button from '../common/Button';
import Modal from '../common/Modal';

/**
 * 크레딧이 모자랄 때 "바로 충전하시겠습니까?" 를 묻는 공용 확인창.
 * 취소는 회색, 결제하기는 파란색이며 결제하기를 누르면 충전 화면으로 이동한다.
 * 충전이 끝나면 returnTo 로 돌아와 하던 일을 이어 간다.
 */
export default function InsufficientCreditDialog({
  open,
  chargeAmount,
  returnTo,
  onClose,
  children,
}: {
  open: boolean;
  chargeAmount: number;
  returnTo: string;
  onClose: () => void;
  children?: React.ReactNode;
}) {
  const router = useRouter();
  return (
    <Modal
      open={open}
      title="크레딧이 부족합니다. 바로 충전하시겠습니까?"
      onClose={onClose}
      footer={(
        <>
          <Button className="btn-muted" onClick={onClose}>취소</Button>
          <Button
            variant="primary"
            onClick={() => router.push(
              `${ROUTES.creditCharge}?amount=${chargeAmount}&returnTo=${encodeURIComponent(returnTo)}`,
            )}
          >
            결제하기
          </Button>
        </>
      )}
    >
      <p>
        부족분을 기준으로 <strong>{formatWon(chargeAmount)}</strong> 충전을 제안합니다.
        충전을 마치면 원래 화면으로 돌아옵니다.
      </p>
      {children}
    </Modal>
  );
}
