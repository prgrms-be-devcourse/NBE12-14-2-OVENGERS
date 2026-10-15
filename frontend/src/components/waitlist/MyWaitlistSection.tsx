'use client';

import { useCallback, useState } from 'react';
import { cancelWaitlist, getMyWaitlist } from '../../api/waitlistApi';
import { getSpace } from '../../api/spaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { formatDateLabel } from '../../utils/date';
import { splitLocalDateTime } from '../../utils/waitlist';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';

/**
 * 내 예약 대기 목록과 취소. 서버는 시작 전 WAITING 만 돌려준다(GET /waitlist/me).
 * position 은 "신청 순서(참고용)"다 — 자리가 나면 전원에게 알리고 예약은 선착순이므로 순서가 자리를 보장하지 않는다.
 * 비어 있으면 아무것도 그리지 않아 예약 목록 화면을 어지럽히지 않는다.
 */
export default function MyWaitlistSection() {
  const [confirmingId, setConfirmingId] = useState<number | null>(null);

  const fetchMine = useCallback(async () => {
    const entries = await getMyWaitlist();
    const spaceIds = [...new Set(entries.map((entry) => entry.spaceId))];
    const spaces = await Promise.all(spaceIds.map((id) => getSpace(id).catch(() => null)));
    const names = new Map(spaceIds.map((id, index) => [id, spaces[index]?.name ?? `오피스 #${id}`]));
    return entries.map((entry) => ({ ...entry, spaceName: names.get(entry.spaceId) ?? `오피스 #${entry.spaceId}` }));
  }, []);
  const { data, error, run } = useAsync(fetchMine, [fetchMine]);

  const { execute: cancel, loading: cancelling, error: cancelError } = useAction(
    async (waitlistEntryId: number) => {
      await cancelWaitlist(waitlistEntryId);
      setConfirmingId(null);
      await run();
    },
  );

  const handleCancel = async (waitlistEntryId: number) => {
    try {
      await cancel(waitlistEntryId);
    } catch {
      // 오류 문구는 cancelError 로 표시한다.
    }
  };

  const entries = data ?? [];
  if (!error && entries.length === 0) return null;

  return (
    <section className="section" aria-labelledby="my-waitlist-title">
      <h2 id="my-waitlist-title">내 대기</h2>
      <p className="muted">자리가 나면 알려 드립니다. 알림은 안내일 뿐 예약은 선착순입니다.</p>
      <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />
      <ErrorMessage error={cancelError} />
      {entries.map((entry) => {
        const start = splitLocalDateTime(entry.startTime);
        const end = splitLocalDateTime(entry.endTime);
        const confirming = confirmingId === entry.id;
        return (
          <div className="panel waitlist-row" key={entry.id}>
            <div>
              <strong>{entry.spaceName}</strong>
              <p>
                {formatDateLabel(start.date)} {start.time} ~ {end.time}
              </p>
              <p className="muted">신청 순서 {entry.position}번째 (참고용)</p>
            </div>
            {confirming ? (
              <div className="row wrap">
                <Button variant="danger" size="small" loading={cancelling} onClick={() => handleCancel(entry.id)}>
                  대기 취소 확정
                </Button>
                <Button size="small" disabled={cancelling} onClick={() => setConfirmingId(null)}>
                  닫기
                </Button>
              </div>
            ) : (
              <Button size="small" onClick={() => setConfirmingId(entry.id)}>
                대기 취소
              </Button>
            )}
          </div>
        );
      })}
    </section>
  );
}
