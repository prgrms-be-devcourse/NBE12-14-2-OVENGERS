import type { ReservationStatus } from '../../types/api';
import { RESERVATION_STATUS_META } from '../../constants/enums';
import { MetaBadge } from '../common/Badge';

export default function ReservationStatusBadge({ status }: { status: ReservationStatus }) {
  return <MetaBadge meta={RESERVATION_STATUS_META[status]} fallback={status} />;
}
