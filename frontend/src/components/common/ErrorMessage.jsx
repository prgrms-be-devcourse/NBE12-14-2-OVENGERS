import { toMessage } from '../../constants/errorCodes';
import Button from './Button';

/** ApiError 를 그대로 넘기면 코드에 맞는 한국어 문구로 보여줍니다. */
export default function ErrorMessage({ error, fallback, onRetry }) {
  if (!error) return null;
  return (
    <div className="form-error" role="alert">
      {toMessage(error, fallback)}
      {onRetry && (
        <div className="actions" style={{ marginTop: 12 }}>
          <Button size="small" onClick={onRetry}>
            다시 시도
          </Button>
        </div>
      )}
    </div>
  );
}
