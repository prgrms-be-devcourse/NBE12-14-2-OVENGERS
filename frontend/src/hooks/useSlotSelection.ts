/**
 * 슬롯 선택 규칙 (프로토타입과 동일):
 * 1) 처음 누른 슬롯이 시작 슬롯이 됩니다.
 * 2) 다음에 누른 슬롯이 마지막 슬롯이 되고, 사이 슬롯이 함께 선택됩니다.
 * 3) 사이에 예약된 슬롯이 하나라도 있으면 새 시작 슬롯으로 다시 잡습니다.
 * 4) 선택된 슬롯을 다시 누르면 선택을 해제합니다.
 */

import { useCallback, useMemo, useState } from 'react';
import type { DecoratedSlot } from '../types/api';
import { endTimeOf, isContinuousRange, slotsInRange, toMinutes } from '../utils/slot';

export interface SlotSelection {
  startTime: string | null;
  endTime: string | null;
  selectedSlots: string[];
  slotCount: number;
  hasSelection: boolean;
  isSelected: (slotStartTime: string) => boolean;
  select: (slotStartTime: string) => void;
  clear: () => void;
}

export function useSlotSelection(slots: DecoratedSlot[]): SlotSelection {
  const [startTime, setStartTime] = useState<string | null>(null);
  const [lastSlotStart, setLastSlotStart] = useState<string | null>(null);

  const clear = useCallback(() => {
    setStartTime(null);
    setLastSlotStart(null);
  }, []);

  const select = useCallback(
    (slotStartTime: string) => {
      const slot = slots.find((s) => s.startTime === slotStartTime);
      if (!slot?.selectable) return;

      if (!startTime || (startTime && lastSlotStart)) {
        setStartTime(slotStartTime);
        setLastSlotStart(null);
        return;
      }
      if (slotStartTime === startTime) {
        clear();
        return;
      }

      const [from, to] =
        toMinutes(slotStartTime) > toMinutes(startTime)
          ? [startTime, slotStartTime]
          : [slotStartTime, startTime];

      if (!isContinuousRange(slots, from, endTimeOf(to))) {
        // 사이에 예약된 슬롯이 있으면 범위를 만들 수 없으므로 새 시작점으로 잡습니다.
        setStartTime(slotStartTime);
        setLastSlotStart(null);
        return;
      }
      setStartTime(from);
      setLastSlotStart(to);
    },
    [slots, startTime, lastSlotStart, clear],
  );

  const selectedSlots = useMemo(() => {
    if (!startTime) return [];
    if (!lastSlotStart) return [startTime];
    return slotsInRange(startTime, endTimeOf(lastSlotStart));
  }, [startTime, lastSlotStart]);

  const endTime = useMemo(() => {
    if (!startTime) return null;
    return endTimeOf(lastSlotStart ?? startTime);
  }, [startTime, lastSlotStart]);

  return {
    startTime,
    endTime,
    selectedSlots,
    slotCount: selectedSlots.length,
    hasSelection: selectedSlots.length > 0,
    isSelected: (slotStartTime: string) => selectedSlots.includes(slotStartTime),
    select,
    clear,
  };
}

export default useSlotSelection;
