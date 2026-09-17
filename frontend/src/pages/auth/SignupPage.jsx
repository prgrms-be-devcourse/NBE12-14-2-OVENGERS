import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useAction } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';

export default function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '', passwordConfirm: '', nickname: '' });
  const [validation, setValidation] = useState({});
  const { execute, loading, error } = useAction(signup);

  const validate = () => {
    const next = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = '이메일 형식을 확인해 주세요.';
    if (form.password.length < 8) next.password = '비밀번호는 8자 이상이어야 합니다.';
    if (form.password !== form.passwordConfirm) next.passwordConfirm = '비밀번호가 일치하지 않습니다.';
    if (!form.nickname.trim()) next.nickname = '닉네임을 입력해 주세요.';
    setValidation(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!validate()) return;
    try {
      await execute({ email: form.email, password: form.password, nickname: form.nickname });
      navigate(ROUTES.spaces, { replace: true });
    } catch {
      // 오류 문구는 ErrorMessage 가 보여줍니다.
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-cover" aria-hidden="true">
        <div>
          <h2>30분 단위로 필요한 만큼만</h2>
          <p>가입 후 바로 공간을 예약할 수 있습니다.</p>
        </div>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <h1>회원가입</h1>
        <p>이메일과 비밀번호만으로 가입합니다.</p>

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
          help="8자 이상. BCrypt 로 해시하여 저장하며 원문은 보관하지 않습니다."
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
          이미 계정이 있으신가요? <Link to={ROUTES.login} className="soft-link">로그인</Link>
        </p>
      </form>
    </div>
  );
}
