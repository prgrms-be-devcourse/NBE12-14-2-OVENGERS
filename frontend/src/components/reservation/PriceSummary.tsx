import type { Space } from '../../types/api';
import { formatDateLabel, formatDuration, formatTimeRange, minutesBetween } from '../../utils/date';
import { formatWon } from '../../utils/price';

/**
 * 결제 전 예상 금액을 보여줍니다.
 * 확정 금액은 서버가 계산하며(FR-RESV-05), serverAmount 가 오면 그 값을 우선 표시합니다.
 */
export interface PriceSummaryProps {
  space?: Space | null;
  date?: string;
  startTime: string | null;
  endTime: string | null;
  slotCount: number;
  clientAmount: number;
  /** 서버가 확정한 금액. 있으면 이 값을 우선 표시합니다. */
  serverAmount?: number | null;
}

export default function PriceSummary({
  space,
  date,
  startTime,
  endTime,
  slotCount,
  clientAmount,
  serverAmount,
}: PriceSummaryProps) {
  const amount = serverAmount ?? clientAmount;
  const hasSelection = Boolean(startTime && endTime);

  return (
    <div>
      <div className="definition">
        <span>공간</span>
        <strong>{space?.name ?? '-'}</strong>
      </div>
      <div className="definition">
        <span>날짜</span>
        <strong>{date ? formatDateLabel(date) : '-'}</strong>
      </div>
      <div className="definition">
        <span>이용 시간</span>
        <strong>
          {hasSelection
            ? `${formatTimeRange(startTime, endTime)} (${formatDuration(minutesBetween(startTime as string, endTime as string))})`
            : '시간을 선택해 주세요'}
        </strong>
      </div>
      <div className="definition">
        <span>요금</span>
        <strong>
          {space ? `${formatWon(space.pricePerSlot)} × ${slotCount || 0}슬롯` : '-'}
        </strong>
      </div>
      <div className="definition total">
        <span>결제 금액</span>
        <strong>{hasSelection ? formatWon(amount) : '-'}</strong>
      </div>
    </div>
  );
}
