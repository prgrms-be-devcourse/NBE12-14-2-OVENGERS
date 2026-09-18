'use client';

import { useCallback, useEffect, useState } from 'react';
import type { IssuedDoorToken } from '../../types/api';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import {
  cancelReservation,
  checkOutReservation,
  extendReservation,
  getMyReservation,
} from '../../api/reservationApi';
import { issueDoorToken } from '../../api/doorAccessApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { useAuth } from '../../hooks/useAuth';
import { ROUTES } from '../../constants/routePaths';
import { CHECK_IN_DEADLINE_MINUTES, RESERVATION_STATUS } from '../../constants/enums';
import { formatDateLabel, formatDateTime, formatTimeRange, toDate } from '../../utils/date';
import { formatWon } from '../../utils/price';
import { formatReservationNo } from '../../utils/format';
import ReservationStatusBadge from '../../components/reservation/ReservationStatusBadge';
import ReservationStatusHistory from '../../components/reservation/ReservationStatusHistory';
import CancelRefundInfo from '../../components/reservation/CancelRefundInfo';
import ExtendReservationDialog from '../../components/reservation/ExtendReservationDialog';
import CheckOutButton from '../../components/reservation/CheckOutButton';
import AccessKeyPanel from '../../components/access/AccessKeyPanel';
import AccessKeyIssueButton from '../../components/access/AccessKeyIssueButton';
import Button from '../../components/common/Button';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import Toast from '../../components/common/Toast';

export default function ReservationDetailPage() {
  const { reservationId } = useParams<{ reservationId: string }>();
  const router = useRouter();
  const { refreshMember } = useAuth();
  const [issuedKey, setIssuedKey] = useState<IssuedDoorToken | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [extending, setExtending] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const fetchReservation = useCallback(() => getMyReservation(reservationId), [reservationId]);
  const { data: reservation, loading, error, run: reload } = useAsync(fetchReservation, [fetchReservation]);

  const cancel = useAction(async () => {
    await cancelReservation(reservationId);
    setIssuedKey(null);
    setCancelling(false);
    setToast('예약이 취소되었습니다. 기존 출입 키도 사용할 수 없습니다.');
    await refreshMember();
    await reload();
  });

  const extend = useAction(async ({ newEndTime }: { newEndTime: string }) => {
    if (!reservation) return;
    await extendReservation(reservationId, { newEndTime, expectedEndTime: reservation.endTime });
    setExtending(false);
    setToast('이용 시간이 연장되었습니다.');
    await refreshMember();
    await reload();
  });

  const checkOut = useAction(async () => {
    await checkOutReservation(reservationId);
    setIssuedKey(null);
    setToast('체크아웃이 완료되었습니다.');
    await reload();
  });

  const issue = useAction(async () => {
    const result = await issueDoorToken(reservationId);
    // 원문은 이 응답에서만 내려옵니다. 다시 조회할 수 없습니다.
    setIssuedKey(result);
    setToast('출입 키가 발급되었습니다.');
    await reload();
    return result;
  });

  // 아직 결제 전이면 결제 화면으로 보냅니다(react-router 의 <Navigate/> 대체).
  const held = reservation?.status === RESERVATION_STATUS.HELD;
  useEffect(() => {
    if (held) router.replace(ROUTES.reservationPayment(reservationId));
  }, [held, reservationId, router]);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} onRetry={reload} />;
  if (!reservation) return null;
  if (held) return <LoadingSpinner label="결제 화면으로 이동합니다…" />;

  const status = reservation.status;
  const confirmed = status === RESERVATION_STATUS.CONFIRMED;
  const inUse = status === RESERVATION_STATUS.IN_USE;
  const canIssueKey = confirmed || inUse;

  const now = new Date();
  const startsAt = toDate(reservation.date, reservation.startTime);
  const endsAt = toDate(reservation.date, reservation.endTime);
  // 날짜가 다른 예약(며칠 뒤 예약 등)에도 안전하도록 Date 차이로 직접 계산한다.
  // 59분 59초를 60분으로 올림해 전액 환불로 잘못 안내하지 않도록 내림한다.
  const minutesUntilStart = Math.floor((startsAt.getTime() - now.getTime()) / 60000);
  const canCancel = confirmed && now < startsAt;
  const canExtend = (confirmed || inUse) && now < endsAt;

  return (
    <>
      <nav className="crumb" aria-label="현재 위치">
        <Link href={ROUTES.reservations}>내 예약</Link>
        <span>›</span>
        <span>{formatReservationNo(reservation.reservationId)}</span>
      </nav>

      <div className="split">
        <div>
          <div className="pagehead">
            <div>
              <div className="row wrap">
                <ReservationStatusBadge status={status} />
                <span className="muted">{formatReservationNo(reservation.reservationId)}</span>
              </div>
              <h1>{reservation.spaceName}</h1>
              <p>{reservation.spaceLocation}</p>
            </div>
          </div>

          <div className="summary-strip">
            <div>
              <span>이용 날짜</span>
              <strong>{formatDateLabel(reservation.date)}</strong>
            </div>
            <div>
              <span>이용 시간</span>
              <strong>{formatTimeRange(reservation.startTime, reservation.endTime)}</strong>
            </div>
            <div>
              <span>결제 금액</span>
              <strong>{formatWon(reservation.totalAmount)}</strong>
            </div>
          </div>

          {status === RESERVATION_STATUS.EXPIRED && (
            <section className="panel">
              <h3>결제 시간이 끝나 취소되었습니다</h3>
              <p>결제 대기 시간이 지나 예약이 취소되었습니다. 공간과 시간을 다시 선택해 주세요.</p>
            </section>
          )}

          {status === RESERVATION_STATUS.NO_SHOW && (
            <section className="panel">
              <h3>노쇼로 처리되었습니다</h3>
              <p>체크인 마감 시각(시작 후 {CHECK_IN_DEADLINE_MINUTES}분)까지 출입하지 않아 예약이 종료되었습니다. 환불되지 않습니다.</p>
            </section>
          )}

          <section className="panel">
            <h3>결제 내역</h3>
            <div className="definition">
              <span>확정 당시 요금</span>
              <strong>{formatWon(reservation.pricePerSlotSnapshot)} / 30분</strong>
            </div>
            <div className="definition">
              <span>총 결제 금액</span>
              <strong>{formatWon(reservation.totalAmount)}</strong>
            </div>
            {reservation.checkedInAt && (
              <div className="definition">
                <span>최초 체크인</span>
                <strong>{formatDateTime(reservation.checkedInAt)}</strong>
              </div>
            )}
            {reservation.checkedOutAt && (
              <div className="definition">
                <span>체크아웃</span>
                <strong>{formatDateTime(reservation.checkedOutAt)}</strong>
              </div>
            )}
            {reservation.cancelledAt && (
              <div className="definition">
                <span>취소 시각</span>
                <strong>{formatDateTime(reservation.cancelledAt)}</strong>
              </div>
            )}
            <p className="note">공간 요금이 바뀌어도 이미 확정된 예약의 금액은 바뀌지 않습니다.</p>
          </section>

          {status === RESERVATION_STATUS.CANCELLED && <CancelRefundInfo />}

          <section className="section">
            <h2>예약 타임라인</h2>
            <ReservationStatusHistory histories={reservation.statusHistories} />
          </section>
        </div>

        <aside className="sticky">
          <>
              <AccessKeyPanel
                accessKey={issuedKey?.accessKey}
                issuedAt={issuedKey?.issuedAt ?? reservation.accessKey?.issuedAt}
                notice={
                  canIssueKey
                    ? '예약 시간이 지나거나 예약을 취소하면 이 키는 사용할 수 없습니다.'
                    : '확정된 예약만 출입 키를 발급받을 수 있습니다.'
                }
              />

              {canIssueKey && (
                <div className="panel" style={{ marginTop: 24 }}>
                  <ErrorMessage error={issue.error} />
                  <AccessKeyIssueButton
                    hasActiveKey={Boolean(reservation.accessKey?.active)}
                    loading={issue.loading}
                    disabled={false}
                    onIssue={() => issue.execute().catch(() => {})}
                  />
                </div>
              )}

              {inUse && (
                <div className="panel" style={{ marginTop: 24 }}>
                  <CheckOutButton
                    loading={checkOut.loading}
                    error={checkOut.error}
                    onCheckOut={() => checkOut.execute()}
                  />
                </div>
              )}

              {canExtend && (
                <div className="panel" style={{ marginTop: 24 }}>
                  <Button wide onClick={() => setExtending(true)}>
                    이용 시간 연장
                  </Button>
                </div>
              )}

              {canCancel && (
                <div className="panel" style={{ marginTop: 24 }}>
                  <Button variant="danger" wide onClick={() => setCancelling(true)}>
                    예약 취소
                  </Button>
                  <p className="form-help">
                    이용 시작 전까지 예약자 본인만 취소할 수 있습니다. 시작 이후에는 체크아웃으로만
                    종료할 수 있습니다.
                  </p>
                </div>
              )}

              {confirmed && <CancelRefundInfo minutesUntilStart={minutesUntilStart} />}
          </>

          <p className="note">
            출입은 <Link href={ROUTES.door} className="soft-link">모의 출입</Link>에서 확인합니다.
          </p>
        </aside>
      </div>

      <ConfirmDialog
        open={cancelling}
        title="예약을 취소할까요?"
        description="취소하면 되돌릴 수 없습니다. 발급받은 출입 키도 즉시 사용할 수 없게 됩니다."
        confirmLabel="예약 취소"
        confirmVariant="danger"
        loading={cancel.loading}
        onClose={() => setCancelling(false)}
        onConfirm={() => cancel.execute().catch(() => {})}
      >
        <ErrorMessage error={cancel.error} />
      </ConfirmDialog>

      <ExtendReservationDialog
        open={extending}
        currentEndTime={reservation.endTime}
        pricePerSlotSnapshot={reservation.pricePerSlotSnapshot}
        loading={extend.loading}
        error={extend.error}
        onClose={() => setExtending(false)}
        onConfirm={(payload) => extend.execute(payload).catch(() => {})}
      />

      <Toast message={toast} onClose={() => setToast(null)} />
    </>
  );
}
