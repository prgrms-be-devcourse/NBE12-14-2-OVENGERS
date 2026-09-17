'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { forceCancelReservation, getAdminReservation } from '../../api/adminReservationApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import { ACCESS_RESULT_META, RESERVATION_STATUS, accessReasonLabel } from '../../constants/enums';
import { formatDateLabel, formatDateTime, formatTimeRange } from '../../utils/date';
import { formatWon } from '../../utils/price';
import { formatReservationNo } from '../../utils/format';
import { MetaBadge } from '../../components/common/Badge';
import ReservationStatusBadge from '../../components/reservation/ReservationStatusBadge';
import ReservationStatusHistory from '../../components/reservation/ReservationStatusHistory';
import Button from '../../components/common/Button';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import Input from '../../components/common/Input';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmptyState from '../../components/common/EmptyState';
import Toast from '../../components/common/Toast';

export default function AdminReservationDetailPage() {
  const { reservationId } = useParams<{ reservationId: string }>();
  const [forceCancelling, setForceCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [toast, setToast] = useState<string | null>(null);

  const fetchReservation = useCallback(() => getAdminReservation(reservationId), [reservationId]);
  const { data, loading, error, run: reload } = useAsync(fetchReservation, [fetchReservation]);

  const cancel = useAction(async () => {
    await forceCancelReservation(reservationId, reason.trim());
    setForceCancelling(false);
    setReason('');
    setToast('예약을 강제 취소했습니다. 감사 로그에 기록되었습니다.');
    await reload();
  });

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} onRetry={reload} />;
  if (!data) return null;

  const confirmed = data.status === RESERVATION_STATUS.CONFIRMED;

  return (
    <>
      <nav className="crumb" aria-label="현재 위치">
        <Link href={ROUTES.adminReservations}>예약 관리</Link>
        <span>›</span>
        <span>{formatReservationNo(data.reservationId)}</span>
      </nav>

      <div className="pagehead">
        <div>
          <div className="row wrap">
            <ReservationStatusBadge status={data.status} />
            <span className="muted">{formatReservationNo(data.reservationId)}</span>
          </div>
          <h1>{data.spaceName}</h1>
          <p>
            {data.memberNickname} · {data.memberEmail}
          </p>
        </div>
        {confirmed && (
          <Button variant="danger" onClick={() => setForceCancelling(true)}>
            강제 취소
          </Button>
        )}
      </div>

      <div className="grid2">
        <section className="panel">
          <h3>예약 정보</h3>
          <div className="definition">
            <span>이용 날짜</span>
            <strong>{formatDateLabel(data.date)}</strong>
          </div>
          <div className="definition">
            <span>이용 시간</span>
            <strong>{formatTimeRange(data.startTime, data.endTime)}</strong>
          </div>
          <div className="definition">
            <span>확정 당시 요금</span>
            <strong>{formatWon(data.pricePerSlotSnapshot)} / 30분</strong>
          </div>
          <div className="definition">
            <span>결제 금액</span>
            <strong>{formatWon(data.totalAmount)}</strong>
          </div>
          <div className="definition">
            <span>약관 버전</span>
            <strong>{data.termsVersion}</strong>
          </div>
          <div className="definition">
            <span>확정 시각</span>
            <strong>{formatDateTime(data.createdAt)}</strong>
          </div>
        </section>

        <section className="panel">
          <h3>상태 변경 이력</h3>
          <ReservationStatusHistory histories={data.statusHistories} />
        </section>
      </div>

      <section className="section">
        <h2>출입 시도 기록</h2>
        {data.accessLogs?.length ? (
          <div className="tablebox">
            <table>
              <caption className="sr-only">출입 시도 기록</caption>
              <thead>
                <tr>
                  <th scope="col">시각</th>
                  <th scope="col">요청 공간</th>
                  <th scope="col">결과</th>
                  <th scope="col">사유</th>
                </tr>
              </thead>
              <tbody>
                {data.accessLogs.map((log) => (
                  <tr key={log.id}>
                    <td>{formatDateTime(log.attemptedAt)}</td>
                    <td>{log.requestedSpaceName ?? '-'}</td>
                    <td>
                      <MetaBadge meta={ACCESS_RESULT_META[log.result]} />
                    </td>
                    <td>{accessReasonLabel(log.reasonCode)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="출입 시도가 없습니다" description="아직 이 예약으로 출입을 시도하지 않았습니다." />
        )}
      </section>

      <p className="note">
        관리자는 전체 예약을 조회하고 사유와 함께 강제 취소할 수 있지만, 다른 회원의 출입 키를
        발급받을 수는 없습니다.
      </p>

      <ConfirmDialog
        open={forceCancelling}
        title="예약을 강제 취소할까요?"
        description="예약자에게 통보되지 않습니다. 사유는 감사 로그에 남습니다."
        confirmLabel="강제 취소"
        confirmVariant="danger"
        loading={cancel.loading}
        onClose={() => setForceCancelling(false)}
        onConfirm={() => cancel.execute().catch(() => {})}
      >
        <Input
          label="사유"
          required
          maxLength={500}
          help="1~500자. 필수 입력입니다."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
        <ErrorMessage error={cancel.error} />
      </ConfirmDialog>

      <Toast message={toast} onClose={() => setToast(null)} />
    </>
  );
}
