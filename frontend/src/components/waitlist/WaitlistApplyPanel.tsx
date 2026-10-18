'use client';

import { useState } from 'react';
import Link from 'next/link';
import { applyWaitlist } from '../../api/waitlistApi';
import { ApiError } from '../../api/client';
import { ERROR_CODE } from '../../constants/errorCodes';
import { ROUTES } from '../../constants/routePaths';
import { useAction } from '../../hooks/useApi';
import { formatDateLabel, formatDuration, formatTimeRange, minutesBetween } from '../../utils/date';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';

/**
 * 예약 대기 신청 패널(공간 상세의 오른쪽 영역).
 * 규칙 판단(점유 여부·3건 한도·중복·본인 예약 겹침)은 서버가 하고, 화면은 구간 선택과 안내만 맡는다.
 * 대기는 자리를 보장하지 않는다 — 자리가 나면 대기자 전원에게 알리고 예약은 선착순이다.
 */
export interface WaitlistApplyPanelProps {
  spaceId: number;
  spaceName: string;
  date: string;
  startTime: string | null;
  endTime: string | null;
  /** 선택 구간에 예약된 칸이 1개 이상인지. 없으면 대기가 아니라 바로 예약해야 한다. */
  needsWaitlist: boolean;
  /** 신청 직전에 자리가 비는 등 화면의 슬롯 현황이 낡았을 때 새로 불러오게 한다. */
  onStale: () => void;
}

export default function WaitlistApplyPanel({
  spaceId,
  spaceName,
  date,
  startTime,
  endTime,
  needsWaitlist,
  onStale,
}: WaitlistApplyPanelProps) {
  // 같은 구간을 다시 눌러 중복 신청하지 않도록, 신청이 끝난 구간 키를 기억한다.
  const selectionKey = `${date}|${startTime}|${endTime}`;
  const [appliedKey, setAppliedKey] = useState<string | null>(null);
  const applied = appliedKey === selectionKey;

  const { execute, loading, error } = useAction(async () => {
    if (!startTime || !endTime) return;
    await applyWaitlist({ spaceId, date, startTime, endTime });
  });

  const handleApply = async () => {
    try {
      await execute();
      setAppliedKey(selectionKey);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === ERROR_CODE.WAITLIST_NOT_NEEDED) {
        onStale();
      }
    }
  };

  const hasSelection = Boolean(startTime && endTime);
  const disabledReason = !hasSelection
    ? '대기할 시간을 선택해 주세요.'
    : !needsWaitlist
      ? '선택한 시간에 예약된 칸이 없습니다. "바로 예약"에서 예약해 주세요.'
      : null;

  return (
    <>
      <div className="definition">
        <span>오피스</span>
        <strong>{spaceName}</strong>
      </div>
      <div className="definition">
        <span>날짜</span>
        <strong>{formatDateLabel(date)}</strong>
      </div>
      <div className="definition">
        <span>대기 시간</span>
        <strong>
          {startTime && endTime
            ? `${formatTimeRange(startTime, endTime)} (${formatDuration(minutesBetween(startTime, endTime))})`
            : '시간을 선택해 주세요'}
        </strong>
      </div>

      <div className="divider" />

      <ErrorMessage error={error} />
      {applied && (
        <p role="status">
          <strong>대기 신청이 완료되었습니다.</strong>{' '}
          <Link href={ROUTES.waitlist}>빈자리 알림 보기</Link>
        </p>
      )}
      <Button
        variant="primary"
        wide
        loading={loading}
        disabled={Boolean(disabledReason) || applied}
        onClick={handleApply}
      >
        {applied ? '대기 신청 완료' : '대기 신청하기'}
      </Button>
      {disabledReason && <p className="form-help">{disabledReason}</p>}

      <p className="space-note muted">
        자리가 나면 대기자 전원에게 알려 드리며, 예약은 먼저 신청한 분이 확정됩니다. 알림이 자리를 보장하지는
        않습니다. 대기는 최대 3건까지 신청할 수 있습니다.
      </p>
    </>
  );
}
