'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '../../hooks/useAuth';
import { useAction } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  // 보호된 화면에서 밀려온 경우 RequireAuth 가 붙여 준 ?redirect= 로 되돌아갑니다.
  const searchParams = useSearchParams();
  const [form, setForm] = useState({ email: '', password: '' });
  const { execute, loading, error } = useAction(login);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      await execute(form);
      router.replace(searchParams.get('redirect') || ROUTES.spaces);
    } catch {
      // 오류 문구는 ErrorMessage 가 보여줍니다.
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-cover" aria-hidden="true">
        <div>
          <h2>예약이 곧 열쇠입니다</h2>
          <p>로그인하면 예약과 출입 키를 함께 관리할 수 있습니다.</p>
        </div>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <h1>로그인</h1>
        <p>Slot Key 계정으로 로그인해 주세요.</p>

        <Input
          label="이메일"
          type="email"
          required
          autoComplete="email"
          value={form.email}
          onChange={(event) => setForm({ ...form, email: event.target.value })}
        />
        <Input
          label="비밀번호"
          type="password"
          required
          autoComplete="current-password"
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />

        <ErrorMessage error={error} />

        <Button type="submit" variant="primary" wide loading={loading}>
          로그인
        </Button>

        <p className="note">
          아직 계정이 없으신가요? <Link href={ROUTES.signup} className="soft-link">회원가입</Link>
        </p>
      </form>
    </div>
  );
}
