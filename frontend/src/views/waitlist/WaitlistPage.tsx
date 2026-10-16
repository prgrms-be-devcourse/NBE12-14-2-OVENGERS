'use client';

import { useCallback, useState } from 'react';
import { cancelWaitlist, getMyWaitlist } from '../../api/waitlistApi';
import { getSpace } from '../../api/spaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { formatDateLabel } from '../../utils/date';
import { splitLocalDateTime } from '../../utils/waitlist';
import Link from 'next/link';
import SpacePhoto from '../../components/space/SpacePhoto';
import { ROUTES } from '../../constants/routePaths';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import LoadingSpinner from '../../components/common/LoadingSpinner';

/**
 * 내 예약 대기 목록과 취소. 서버는 시작 전 WAITING 만 돌려준다(GET /waitlist/me).
 * position 은 "신청 순서(참고용)"다 — 자리가 나면 전원에게 알리고 예약은 선착순이므로 순서가 자리를 보장하지 않는다.
 * 예약(내 예약)과 별개 화면이다 — 대기는 아직 자리를 가진 것이 아니다.
 */
export default function WaitlistPage() {
  const [confirmingId, setConfirmingId] = useState<number | null>(null);

  const fetchMine = useCallback(async () => {
    const entries = await getMyWaitlist();
    const spaceIds = [...new Set(entries.map((entry) => entry.spaceId))];
    const spaces = await Promise.all(spaceIds.map((id) => getSpace(id).catch(() => null)));
    const names = new Map(spaceIds.map((id, index) => [id, spaces[index]?.name ?? `오피스 #${id}`]));
    const images = new Map(spaceIds.map((id, index) => [id, spaces[index]?.imagePath ?? null]));
    return entries.map((entry) => ({
      ...entry,
      spaceName: names.get(entry.spaceId) ?? `오피스 #${entry.spaceId}`,
      spaceImagePath: images.get(entry.spaceId) ?? null,
    }));
  }, []);
  const { data, loading, error, run } = useAsync(fetchMine, [fetchMine]);

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
  return (
    <>
      <div className="pagehead">
        <div>
          <p className="page-kicker waitlist-kicker">MY WAITLIST</p>
          <h1>내 대기</h1>
          <p>
            자리가 나면 알려 드립니다. 알림은 안내일 뿐 예약은 선착순이며, 대기는 예약이 아닙니다.
            확정된 일정은 <Link href={ROUTES.reservations}>내 예약</Link>에서 확인하세요.
          </p>
        </div>
        <Link href={ROUTES.spaces} className="btn primary">
          오피스 둘러보기
        </Link>
      </div>
      {loading && <LoadingSpinner />}
      <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />
      <ErrorMessage error={cancelError} />
      {!loading && !error && entries.length === 0 && (
        <EmptyState
          title="대기 중인 항목이 없습니다"
          description="예약이 찬 시간대는 오피스 상세에서 '빈자리 대기 신청'을 할 수 있어요."
          action={<Link href={ROUTES.spaces} className="btn primary">오피스 찾기</Link>}
        />
      )}
      {entries.map((entry) => {
        const start = splitLocalDateTime(entry.startTime);
        const end = splitLocalDateTime(entry.endTime);
        const confirming = confirmingId === entry.id;
        return (
          <div className="panel waitlist-row" key={entry.id}>
            <Link className="waitlist-row-link" href={ROUTES.spaceDetail(entry.spaceId)}>
              <SpacePhoto src={entry.spaceImagePath} alt={entry.spaceName} className="waitlist-row-photo" />
              <div>
                <strong>{entry.spaceName}</strong>
                <p>
                  {formatDateLabel(start.date)} {start.time} ~ {end.time}
                </p>
                <p className="muted">신청 순서 {entry.position}번째 (참고용)</p>
              </div>
            </Link>
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
    </>
  );
}
