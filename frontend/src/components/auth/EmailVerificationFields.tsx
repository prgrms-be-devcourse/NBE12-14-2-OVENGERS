'use client';

import { useEffect, useState } from 'react';
import type { VerificationPurpose } from '../../types/api';
import { checkVerification, issueVerification } from '../../api/authApi';
import { useAction } from '../../hooks/useApi';
import Input from '../common/Input';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';

interface Props {
  email: string;
  purpose: VerificationPurpose;
  onGrantChange: (grant: string) => void;
}

export default function EmailVerificationFields({ email, purpose, onGrantChange }: Props) {
  const [challengeId, setChallengeId] = useState<number | null>(null);
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(false);
  const [resendAvailableAt, setResendAvailableAt] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const issue = useAction(() => issueVerification(email, purpose));
  const check = useAction(() => {
    if (challengeId === null) throw new Error('인증번호를 먼저 요청해 주세요.');
    return checkVerification(email, purpose, challengeId, code);
  });

  useEffect(() => {
    setChallengeId(null);
    setCode('');
    setVerified(false);
    setResendAvailableAt(null);
    onGrantChange('');
  }, [email, purpose, onGrantChange]);

  useEffect(() => {
    if (!resendAvailableAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [resendAvailableAt]);

  const handleIssue = async () => {
    if (!email.trim()) return;
    try {
      const result = await issue.execute();
      if (!result) return;
      setChallengeId(result.challengeId);
      setResendAvailableAt(result.resendAvailableAt);
      setVerified(false);
      onGrantChange('');
    } catch {
      // useAction 상태로 오류를 표시합니다.
    }
  };

  const handleCheck = async () => {
    try {
      const result = await check.execute();
      if (!result) return;
      setVerified(true);
      onGrantChange(result.grant);
    } catch {
      setVerified(false);
      onGrantChange('');
    }
  };

  const resendMessage = resendAvailableAt
    ? `${new Date(resendAvailableAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} 이후 재발송할 수 있습니다.`
    : '인증번호는 3분 동안 유효합니다.';
  const resendReady = !resendAvailableAt || new Date(resendAvailableAt).getTime() <= now;

  return (
    <fieldset className="verification-box">
      <legend>이메일 인증</legend>
      <p className="form-help">가입 완료를 위해 이메일로 받은 8자리 인증번호를 입력해 주세요.</p>
      <div className="verification-row">
        <Input
          label="인증번호"
          inputMode="numeric"
          maxLength={8}
          placeholder="8자리 숫자"
          disabled={verified}
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
        />
        <Button type="button" size="small" onClick={handleIssue} loading={issue.loading} disabled={!email.trim() || verified || !resendReady}>
          {challengeId ? '재발송' : '인증번호 받기'}
        </Button>
      </div>
      {challengeId !== null && !verified && (
        <Button type="button" size="small" variant="ghost" onClick={handleCheck} loading={check.loading} disabled={code.length !== 8}>
          인증번호 확인
        </Button>
      )}
      {verified && <p className="verification-success" role="status">이메일 인증이 완료되었습니다.</p>}
      {!verified && <p className="form-help">{resendMessage}</p>}
      <ErrorMessage error={issue.error || check.error} />
    </fieldset>
  );
}
