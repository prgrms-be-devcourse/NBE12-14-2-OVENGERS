/**
 * 로그인 상태를 앱 전역에서 공유합니다.
 *
 * 화면에서 관리자 메뉴를 숨기는 것은 편의일 뿐이고, 실제 권한 판단은 서버가 합니다
 * (docs/requirements.md 5장). 여기의 role 값을 근거로 보안을 가정하지 않습니다.
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { LoginRequest, Member, SignupRequest } from '../types/api';
import { clearTokens, hasSession, setUnauthorizedHandler } from '../api/client';
import * as authApi from '../api/authApi';
import { getMe } from '../api/memberApi';
import { MEMBER_ROLE } from '../constants/enums';
import { AuthContext } from './AuthContext';
import type { AuthContextValue } from './AuthContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [member, setMember] = useState<Member | null>(null);
  const [initializing, setInitializing] = useState(true);

  const clearSession = useCallback(() => {
    clearTokens();
    setMember(null);
  }, []);

  // 재발급까지 실패하면 클라이언트가 이 핸들러를 호출합니다.
  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  // 새로고침 후 저장된 토큰으로 회원 정보를 복구합니다.
  useEffect(() => {
    let cancelled = false;
    async function restore() {
      if (!hasSession()) {
        setInitializing(false);
        return;
      }
      try {
        const me = await getMe();
        if (!cancelled) setMember(me);
      } catch {
        if (!cancelled) clearSession();
      } finally {
        if (!cancelled) setInitializing(false);
      }
    }
    restore();
    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  const login = useCallback(async (credentials: LoginRequest): Promise<Member> => {
    const data = await authApi.login(credentials);
    const me = data.member ?? (await getMe());
    setMember(me);
    return me;
  }, []);

  const signup = useCallback(
    async (form: SignupRequest): Promise<Member> => {
      await authApi.signup(form);
      return login({ email: form.email, password: form.password });
    },
    [login],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    setMember(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      member,
      initializing,
      isAuthenticated: Boolean(member),
      isAdmin: member?.role === MEMBER_ROLE.ADMIN,
      login,
      signup,
      logout,
      refreshMember: async () => {
        setMember(await getMe());
      },
    }),
    [member, initializing, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
