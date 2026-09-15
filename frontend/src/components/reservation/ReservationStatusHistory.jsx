import { formatDateTime } from '../../utils/date';
import { RESERVATION_STATUS_META } from '../../constants/enums';

/** 상태 변경 이력. 수행자가 없는 항목은 스케줄러가 처리한 것입니다. */
export default function ReservationStatusHistory({ histories = [] }) {
  if (!histories.length) return <p className="muted">기록된 상태 변경이 없습니다.</p>;

  return (
    <div className="timeline">
      {histories.map((history) => (
        <div key={history.id}>
          <strong>
            {history.fromStatus
              ? `${RESERVATION_STATUS_META[history.fromStatus]?.label ?? history.fromStatus} → `
              : '예약 생성 · '}
            {RESERVATION_STATUS_META[history.toStatus]?.label ?? history.toStatus}
          </strong>
          <p>{formatDateTime(history.createdAt)}</p>
          <p>수행자: {history.actorNickname ?? '시스템(자동 처리)'}</p>
          {history.reason && <p>사유: {history.reason}</p>}
        </div>
      ))}
    </div>
  );
}
