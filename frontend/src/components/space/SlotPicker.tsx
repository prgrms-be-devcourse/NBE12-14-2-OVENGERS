import type { DecoratedSlot } from '../../types/api';
import { cx } from '../../utils/format';

/**
 * 30분 슬롯 선택기.
 * 화면에서 예약 가능으로 보여도 확정은 서버가 판단합니다.
 * 조회 이후 다른 사용자가 먼저 예약할 수 있기 때문입니다(docs/requirements.md 2장).
 */
export interface SlotPickerProps {
  slots: DecoratedSlot[];
  isSelected: (startTime: string) => boolean;
  onSelect: (startTime: string) => void;
  disabled?: boolean;
}

export default function SlotPicker({ slots, isSelected, onSelect, disabled }: SlotPickerProps) {
  if (!slots?.length) {
    return <p className="muted">이 날짜에는 운영하는 시간이 없습니다.</p>;
  }

  return (
    <>
      <div className="slotgrid" role="group" aria-label="이용 시간 선택">
        {slots.map((slot) => (
          <button
            key={slot.startTime}
            type="button"
            className={cx(isSelected(slot.startTime) && 'selected')}
            disabled={disabled || !slot.selectable}
            aria-pressed={isSelected(slot.startTime)}
            title={slot.reason ?? undefined}
            onClick={() => onSelect(slot.startTime)}
          >
            {slot.startTime}
          </button>
        ))}
      </div>
      <div className="slot-legend" aria-label="시간 선택 상태 안내"><span><i />선택 가능</span><span><i className="chosen" />선택됨</span><span><i className="unavailable" />선택 불가</span></div>
      <p className="form-help">
        시작 시간과 마지막 시간을 차례로 누르면 사이 시간이 함께 선택됩니다.
        취소선이 있는 시간은 이미 예약되었거나 지난 시간입니다.
      </p>
    </>
  );
}
