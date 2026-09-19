import type { ReservationDetailApiResponse } from '../../api/reservationApi';
import { formatDateTime } from '../../utils/date';
import { RESERVATION_STATUS_META } from '../../constants/enums';

export default function ReservationStatusHistory({
  histories = [],
}: {
  histories?: ReservationDetailApiResponse['statusHistory'];
}) {
  if (!histories.length) {
    return <p className="muted">기록된 상태 변경이 없습니다.</p>;
  }

  return (
    <div className="timeline">
      {histories.map((history, index) => (
        <div
          key={`${history.changedAt}-${history.toStatus}-${index}`}
        >
          <strong>
            {history.fromStatus
              ? `${RESERVATION_STATUS_META[history.fromStatus]?.label
                  ?? history.fromStatus} → `
              : '예약 생성 · '}
            {RESERVATION_STATUS_META[history.toStatus]?.label
              ?? history.toStatus}
          </strong>

          <p>{formatDateTime(history.changedAt)}</p>

          {history.reason && <p>사유: {history.reason}</p>}
        </div>
      ))}
    </div>
  );
}