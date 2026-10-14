'use client';

import { useCallback, useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { resetPassword } from '../../api/authApi';
import { ROUTES } from '../../constants/routePaths';
import { useAction } from '../../hooks/useApi';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';
import EmailVerificationFields from '../../components/auth/EmailVerificationFields';

export default function PasswordResetPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [grant, setGrant] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [validation, setValidation] = useState<Record<string, string>>({});
  const action = useAction(() => resetPassword({ email, grant, password, passwordConfirm }));
  const handleGrantChange = useCallback((nextGrant: string) => setGrant(nextGrant), []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!grant) next.grant = '이메일 인증을 완료해 주세요.';
    if (password.length < 8) next.password = '비밀번호는 8자 이상이어야 합니다.';
    if (password !== passwordConfirm) next.passwordConfirm = '비밀번호가 일치하지 않습니다.';
    setValidation(next);
    if (Object.keys(next).length > 0) return;
    try {
      await action.execute();
      router.replace(`${ROUTES.login}?reset=1`);
    } catch {
      // useAction 상태로 오류를 표시합니다.
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-cover" aria-hidden="true">
        <div>
          <h2>안전하게 다시<br />시작하는 계정</h2>
          <p>이메일 인증으로<br />새 비밀번호를 설정하세요.</p>
        </div>
      </div>
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <p className="page-kicker">ACCOUNT RECOVERY</p>
        <h1>비밀번호 재설정</h1>
        <p>가입한 이메일을 인증한 뒤 새 비밀번호를 설정해 주세요.</p>

        <Input
          label="이메일"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <EmailVerificationFields
          email={email}
          purpose="PASSWORD_RESET"
          onGrantChange={handleGrantChange}
        />
        {validation.grant && <p className="form-error" role="alert">{validation.grant}</p>}
        <Input
          label="새 비밀번호"
          type="password"
          required
          autoComplete="new-password"
          help="8자 이상 입력해 주세요."
          error={validation.password}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Input
          label="새 비밀번호 확인"
          type="password"
          required
          autoComplete="new-password"
          error={validation.passwordConfirm}
          value={passwordConfirm}
          onChange={(event) => setPasswordConfirm(event.target.value)}
        />

        <ErrorMessage error={action.error} />
        <Button type="submit" variant="primary" wide loading={action.loading}>
          비밀번호 변경하기
        </Button>
        <p className="note">
          비밀번호가 기억나셨나요? <Link href={ROUTES.login} className="soft-link">로그인</Link>
        </p>
      </form>
    </div>
  );
}
