import { useState } from 'react';
import type { MessageSource } from '../../constants/errorCodes';
import ConfirmDialog from '../common/ConfirmDialog';
import ErrorMessage from '../common/ErrorMessage';
import Select from '../common/Select';
import { formatWon } from '../../utils/price';

const EXTEND_OPTIONS_MINUTES = [30, 60, 90, 120];

const pad = (n: number): string => String(n).padStart(2, '0');

/** "YYYY-MM-DDTHH:mm:ss"(서버 LocalDateTime) 에 분을 더해 같은 형식으로 돌려준다. 날짜 넘김도 처리한다. */
function addMinutes(dateTimeString: string, minutes: number): string {
  const d = new Date(dateTimeString); // 오프셋 없는 ISO 문자열은 로컬 시각으로 해석된다
  d.setMinutes(d.getMinutes() + minutes);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
}

/**
 * 연장. 기존 슬롯은 그대로 두고 추가 슬롯만 확보한다 — 실패해도 원 예약은 무손상이다
 * (core-domain-decisions.md 7-2). 추가 요금은 원 예약의 확정 당시 요금(pricePerSlotSnapshot)
 * 기준으로 계산되어 화면 표시는 참고용이며, 확정 금액은 서버가 다시 계산한다.
 */
export interface ExtendReservationDialogProps {
  open: boolean;
  /** 서버 LocalDateTime 문자열 */
  currentEndTime?: string | null;
  pricePerSlotSnapshot?: number | null;
  loading?: boolean;
  error: MessageSource | null;
  onClose: () => void;
  onConfirm: (payload: { newEndTime: string }) => void;
}

export default function ExtendReservationDialog({
  open,
  currentEndTime,
  pricePerSlotSnapshot,
  loading,
  error,
  onClose,
  onConfirm,
}: ExtendReservationDialogProps) {
  const [extraMinutes, setExtraMinutes] = useState(EXTEND_OPTIONS_MINUTES[0]);
  const newEndTime = currentEndTime ? addMinutes(currentEndTime, extraMinutes) : null;
  const extraAmount = pricePerSlotSnapshot ? (pricePerSlotSnapshot * extraMinutes) / 30 : null;

  return (
    <ConfirmDialog
      open={open}
      title="이용 시간을 연장할까요?"
      description="기존 예약은 그대로 유지되며, 연장한 시간만큼만 추가로 확보됩니다. 이미 다른 예약이 있는 시간대는 연장할 수 없습니다."
      confirmLabel="연장하기"
      loading={loading}
      onClose={onClose}
      onConfirm={() => newEndTime && onConfirm({ newEndTime })}
    >
      <Select
        label="연장할 시간"
        options={EXTEND_OPTIONS_MINUTES.map((m) => ({
          value: m,
          label: m < 60 ? `${m}분` : `${m / 60}시간`,
        }))}
        value={extraMinutes}
        onChange={(event) => setExtraMinutes(Number(event.target.value))}
      />
      <div className="definition">
        <span>변경될 종료 시각</span>
        <strong>{newEndTime ? newEndTime.slice(11, 16) : '-'}</strong>
      </div>
      <div className="definition total">
        <span>추가 결제 예상 금액</span>
        <strong>{extraAmount !== null ? formatWon(extraAmount) : '-'}</strong>
      </div>
      <p className="form-help">잔액이 부족하면 연장이 거절됩니다.</p>
      <ErrorMessage error={error} />
    </ConfirmDialog>
  );
}
