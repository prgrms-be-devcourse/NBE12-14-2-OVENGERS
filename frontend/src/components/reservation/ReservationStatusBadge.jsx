import { RESERVATION_STATUS_META } from '../../constants/enums';
import { MetaBadge } from '../common/Badge';

export default function ReservationStatusBadge({ status }) {
  return <MetaBadge meta={RESERVATION_STATUS_META[status]} fallback={status} />;
}
