import { useEffect, useRef, useState } from 'react';

function remainingSeconds(holdExpiresAt: string): number {
  const diff = new Date(holdExpiresAt).getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / 1000));
}

function formatMMSS(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/**
 * 결제 대기(HOLD) 남은 시간을 1초 단위로 보여줍니다.
 * hold_expires_at 을 지나면 예약이 자동으로 EXPIRED 되므로(core-domain-decisions.md 2-1, 3-2),
 * 0에 도달하면 한 번만 onExpire 를 호출해 화면이 만료 처리를 할 수 있게 합니다.
 */
export interface HoldCountdownProps {
  holdExpiresAt: string;
  onExpire?: () => void;
  compact?: boolean;
}

export default function HoldCountdown({
  holdExpiresAt,
  onExpire,
  compact = false,
}: HoldCountdownProps) {
  const [seconds, setSeconds] = useState(() => remainingSeconds(holdExpiresAt));
  const expiredNotified = useRef(false);

  useEffect(() => {
    expiredNotified.current = false;
    setSeconds(remainingSeconds(holdExpiresAt));

    const timer = setInterval(() => {
      const remainingMilliseconds = new Date(holdExpiresAt).getTime() - Date.now();
      const next = Math.max(0, Math.ceil(remainingMilliseconds / 1000));
      setSeconds(next);
      if (remainingMilliseconds <= 0 && !expiredNotified.current) {
        expiredNotified.current = true;
        onExpire?.();
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [holdExpiresAt, onExpire]);

  const urgent = seconds > 0 && seconds <= 60;

  if (compact) {
    return (
      <span className={`payment-countdown ${seconds === 0 || urgent ? 'urgent' : ''}`} role="timer" aria-live="polite">
        <span aria-hidden="true">◷</span>
        <small>{seconds > 0 ? '결제 대기 시간' : '결제 만료'}</small>
        <strong>{formatMMSS(seconds)}</strong>
      </span>
    );
  }

  return (
    <div className="callout" role="timer" aria-live="polite">
      <div>
        <strong>{seconds > 0 ? '결제 대기 시간' : '결제 대기 시간이 끝났습니다'}</strong>
        <p>
          {seconds > 0
            ? `${formatMMSS(seconds)} 안에 결제를 완료해 주세요. 시간이 지나면 자동으로 취소됩니다.`
            : '새로고침하면 만료 처리된 상태를 확인할 수 있습니다.'}
        </p>
      </div>
      <span className={`badge ${seconds === 0 ? 'red' : urgent ? 'red' : 'cyan'}`} style={{ marginLeft: 'auto' }}>
        {formatMMSS(seconds)}
      </span>
    </div>
  );
}
