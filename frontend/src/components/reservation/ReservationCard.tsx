import Link from 'next/link';
import SpacePhoto from '../space/SpacePhoto';
import type { ReservationSummary } from '../../types/api';
import { ROUTES } from '../../constants/routePaths';
import { formatDateLabel, formatTimeRange } from '../../utils/date';
import { formatWon } from '../../utils/price';
import { formatReservationNo } from '../../utils/format';
import ReservationStatusBadge from './ReservationStatusBadge';
import { RESERVATION_STATUS } from '../../constants/enums';

export default function ReservationCard({ reservation }: { reservation: ReservationSummary }) {
  const targetRoute =
    reservation.status === RESERVATION_STATUS.HELD
      ? ROUTES.reservationPayment(reservation.reservationId)
      : ROUTES.reservationDetail(reservation.reservationId);

  return (
    <article className="booking-row">
      <SpacePhoto src={reservation.spaceImagePath} alt={reservation.spaceName} />
      <div>
        <div className="row wrap">
          <ReservationStatusBadge status={reservation.status} />
          <span className="muted">{formatReservationNo(reservation.reservationId)}</span>
        </div>
        <h3>{reservation.spaceName}</h3>
        <p>
          {formatDateLabel(reservation.date)} ·{' '}
          {formatTimeRange(
            reservation.startTime.slice(11, 16),
            reservation.endTime.slice(11, 16),
          )}
        </p>
      </div>
      <div className="booking-right">
        <div className="price">{formatWon(reservation.totalAmount)}</div>
        <Link href={targetRoute} className="btn small">
          {reservation.status === RESERVATION_STATUS.HELD ? '결제하기' : '상세 보기'}
        </Link>
      </div>
    </article>
  );
}
