'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '../../hooks/useAuth';
import { useAction } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';

export default function SignupPage() {
  const { signup } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ email: '', password: '', passwordConfirm: '', nickname: '' });
  const [validation, setValidation] = useState<
    Partial<Record<'email' | 'password' | 'passwordConfirm' | 'nickname', string>>
  >({});
  const { execute, loading, error } = useAction(signup);

  const validate = () => {
    const next: Partial<Record<'email' | 'password' | 'passwordConfirm' | 'nickname', string>> = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = '이메일 형식을 확인해 주세요.';
    if (form.password.length < 8) next.password = '비밀번호는 8자 이상이어야 합니다.';
    if (form.password !== form.passwordConfirm) next.passwordConfirm = '비밀번호가 일치하지 않습니다.';
    if (!form.nickname.trim()) next.nickname = '닉네임을 입력해 주세요.';
    setValidation(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!validate()) return;
    try {
      await execute({
        email: form.email,
        password: form.password,
        passwordConfirm: form.passwordConfirm,
        nickname: form.nickname,
      });
      router.replace(ROUTES.spaces);
    } catch {
      // 오류 문구는 ErrorMessage 가 보여줍니다.
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-cover" aria-hidden="true">
        <div>
          <h2>좋은 공간에서<br />시작하는 새로운 일</h2>
          <p>나에게 맞는 공간을 찾고<br />필요한 시간만큼 이용하세요.</p>
        </div>
        <small className="auth-image-note">건축 이미지 예시</small>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <p className="page-kicker">GET STARTED</p>
        <h1>회원가입</h1>
        <p>계정을 만들고 나에게 맞는 공간을 찾아보세요.</p>

        <Input
          label="이메일"
          type="email"
          required
          autoComplete="email"
          error={validation.email}
          value={form.email}
          onChange={(event) => setForm({ ...form, email: event.target.value })}
        />
        <Input
          label="비밀번호"
          type="password"
          required
          autoComplete="new-password"
          help="8자 이상 입력해 주세요."
          error={validation.password}
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />
        <Input
          label="비밀번호 확인"
          type="password"
          required
          autoComplete="new-password"
          error={validation.passwordConfirm}
          value={form.passwordConfirm}
          onChange={(event) => setForm({ ...form, passwordConfirm: event.target.value })}
        />
        <Input
          label="닉네임"
          required
          maxLength={30}
          error={validation.nickname}
          value={form.nickname}
          onChange={(event) => setForm({ ...form, nickname: event.target.value })}
        />

        <ErrorMessage error={error} />

        <Button type="submit" variant="primary" wide loading={loading}>
          가입하고 시작하기
        </Button>

        <p className="note">
          이미 계정이 있으신가요? <Link href={ROUTES.login} className="soft-link">로그인</Link>
        </p>
      </form>
    </div>
  );
}
