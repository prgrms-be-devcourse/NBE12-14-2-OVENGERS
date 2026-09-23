import Badge from '../common/Badge';
import type { InquiryStatus } from '../../api/inquiryApi';
export default function InquiryStatusBadge({ status }: { status: InquiryStatus }) {
  return <Badge tone={status === 'ANSWERED' ? 'green' : 'cyan'}>{status === 'ANSWERED' ? '답변 완료' : '답변 대기'}</Badge>;
}
