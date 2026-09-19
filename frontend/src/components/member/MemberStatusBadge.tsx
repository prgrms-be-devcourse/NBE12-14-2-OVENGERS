import type { MemberStatus } from '../../types/api';
import { MEMBER_STATUS_META } from '../../constants/enums';
import { MetaBadge } from '../common/Badge';

export default function MemberStatusBadge({ status }: { status: MemberStatus }) {
  return <MetaBadge meta={MEMBER_STATUS_META[status]} fallback={status} />;
}
