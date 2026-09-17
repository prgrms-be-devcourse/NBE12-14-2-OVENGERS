import { formatAccessKey } from '../../utils/format';
import { formatDateTime } from '../../utils/date';

/**
 * 발급된 출입 키 원문은 이 화면에서 한 번만 보여집니다.
 * 서버에는 해시만 저장되므로 다시 조회할 수 없습니다.
 *
 * 발급에는 시간 제한이 없습니다(core-domain-decisions.md 8-1) — 예약이 확정된 뒤로는
 * 언제든 발급할 수 있으며, 실제 출입은 예약 시작 시각부터만 허용됩니다.
 */
export interface AccessKeyPanelProps {
  accessKey?: string | null;
  issuedAt?: string | null;
  notice?: string;
}

export default function AccessKeyPanel({ accessKey, issuedAt, notice }: AccessKeyPanelProps) {
  if (!accessKey) {
    return (
      <div className="key-card">
        <h2>출입 키</h2>
        <p>아직 발급된 키가 없습니다. 예약이 확정된 뒤 언제든 발급할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="key-card">
      <h2>출입 키가 발급되었습니다</h2>
      <p className="muted">이 화면을 벗어나면 다시 볼 수 없습니다. 필요하면 지금 저장해 주세요.</p>
      <p className="key-code">{formatAccessKey(accessKey)}</p>
      {issuedAt && <p className="muted">발급 시각 {formatDateTime(issuedAt)}</p>}
      {notice && <p className="note">{notice}</p>}
    </div>
  );
}
