'use client';

import { useEffect, useRef, useState } from 'react';
import styles from './AccessKeyPanel.module.css';
import { formatAccessKey } from '../../utils/format';
import { formatDateTime } from '../../utils/date';

/**
 * 발급된 출입 키를 표시하고 원문을 복사합니다.
 * 재방문용 브라우저 보관은 ReservationAccessKey에서 담당합니다.
 *
 * 발급에는 시간 제한이 없습니다(core-domain-decisions.md 8-1) — 예약이 확정된 뒤로는
 * 언제든 발급할 수 있으며, 실제 출입은 예약 시작 시각부터만 허용됩니다.
 */
export interface AccessKeyPanelProps {
  accessKey?: string | null;
  issuedAt?: string | null;
  notice?: string;
  description?: string;
}

export default function AccessKeyPanel({ accessKey, issuedAt, notice, description }: AccessKeyPanelProps) {
  const [feedback, setFeedback] = useState<{ key: string; message: string } | null>(null);
  const copyVersion = useRef(0);

  useEffect(() => {
    return () => { copyVersion.current += 1; };
  }, [accessKey]);

  useEffect(() => {
    if (!feedback) return;
    const timer = window.setTimeout(() => setFeedback(null), 3000);
    return () => window.clearTimeout(timer);
  }, [feedback]);

  const copyKey = async () => {
    if (!accessKey) return;
    const version = ++copyVersion.current;
    try {
      await navigator.clipboard.writeText(accessKey);
      if (version === copyVersion.current) {
        setFeedback({ key: accessKey, message: '클립보드에 복사되었습니다' });
      }
    } catch {
      if (version === copyVersion.current) {
        setFeedback({ key: accessKey, message: '복사하지 못했습니다. 클립보드 권한을 확인하고 다시 눌러 주세요.' });
      }
    }
  };

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
      <h2>출입 키가 준비되었습니다</h2>
      <p className="muted">{description ?? '출입 키를 클릭하면 원문이 복사됩니다.'}</p>
      <button
        type="button"
        className={`key-code ${styles.copyKey}`}
        onClick={copyKey}
        aria-label="출입 키를 클립보드에 복사"
        aria-describedby="access-key-copy-feedback"
      >
        {formatAccessKey(accessKey)}
      </button>
      <p id="access-key-copy-feedback" className={styles.feedback} role="status" aria-live="polite">
        {feedback?.key === accessKey ? feedback.message : '출입 키를 클릭하면 복사됩니다'}
      </p>
      {issuedAt && <p className="muted">발급 시각 {formatDateTime(issuedAt)}</p>}
      {notice && <p className="note">{notice}</p>}
    </div>
  );
}
