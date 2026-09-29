'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useAuth } from '@/hooks/useAuth';
import { useAction } from '@/hooks/useApi';
import { ROUTES } from '@/constants/routePaths';
import { formatCredit } from '@/utils/price';
import Modal from '@/components/common/Modal';
import Input from '@/components/common/Input';
import Button from '@/components/common/Button';
import ErrorMessage from '@/components/common/ErrorMessage';

export default function WithdrawalDialog({ balance, onClose }: {
  balance: number;
  onClose: () => void;
}) {
  const { withdraw } = useAuth();
  const [password, setPassword] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const { execute, loading, error } = useAction(async () => {
    await withdraw(password);
    return true;
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (loading) return;
    if (!password.trim() || !agreed) {
      setValidationError('현재 비밀번호를 입력하고 탈퇴 안내에 동의해주세요.');
      return;
    }
    setValidationError(null);
    try {
      if (!await execute()) return;
      // 보호 화면과 인증 관련 메모리를 함께 종료한다.
      window.location.replace(`${ROUTES.login}?withdrawn=1`);
    } catch {
      // 오류는 아래에 표시한다. 비밀번호는 실패 후에도 다시 입력하게 한다.
      setPassword('');
    }
  }

  return (
    <Modal open title="회원탈퇴" onClose={() => { if (!loading) onClose(); }}>
      <form onSubmit={handleSubmit}>
        <p>탈퇴한 계정은 복구할 수 없으며, 같은 이메일로 다시 가입할 수 없습니다.</p>
        <p>현재 보유한 <strong>{formatCredit(balance)}</strong>은 탈퇴 시 전액 소멸하며 환급되지 않습니다. 처리 시점의 잔액이 적용됩니다.</p>
        <p>결제 대기·예약 확정·이용 중인 예약이 있으면 탈퇴할 수 없습니다. 정지 회원은 운영자에게 문의해주세요.</p>
        <Input
          label="현재 비밀번호"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          disabled={loading}
          onChange={(event) => setPassword(event.target.value)}
        />
        <label className="check">
          <input type="checkbox" required checked={agreed} disabled={loading}
            onChange={(event) => setAgreed(event.target.checked)} />
          <span>계정 복구 불가, 크레딧 소멸 및 동일 이메일 재가입 제한을 확인했으며 탈퇴에 동의합니다.</span>
        </label>
        <ErrorMessage error={validationError} />
        <ErrorMessage error={error} />
        {error?.code === 'WITHDRAWAL_ACTIVE_RESERVATION' && (
          <p><Link href={ROUTES.reservations} className="soft-link" onClick={onClose}>내 예약 확인하기</Link></p>
        )}
        <div className="actions wrap">
          <Button disabled={loading} onClick={onClose}>취소</Button>
          <Button type="submit" variant="danger" loading={loading}
            disabled={!password.trim() || !agreed}>탈퇴하기</Button>
        </div>
      </form>
    </Modal>
  );
}
