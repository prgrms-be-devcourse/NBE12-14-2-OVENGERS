import { ACCESS_REASON_LABEL, ACCESS_RESULT } from '../../constants/enums';
import { formatDateTime } from '../../utils/date';
import { cx } from '../../utils/format';

export default function DoorVerifyResult({ result }) {
  if (!result) return null;
  const allowed = result.result === ACCESS_RESULT.ALLOW;

  return (
    <div className={cx('result', allowed ? 'allow' : 'deny')} role="status" aria-live="polite">
      <strong>{allowed ? '출입이 허용되었습니다' : '출입이 거절되었습니다'}</strong>
      <p>{ACCESS_REASON_LABEL[result.reasonCode] ?? result.reasonCode}</p>
      {allowed && result.spaceName && (
        <p>
          {result.spaceName} · {result.firstCheckIn ? '최초 체크인' : '재입장'}
        </p>
      )}
      {result.attemptedAt && <p className="muted">{formatDateTime(result.attemptedAt)}</p>}
    </div>
  );
}
