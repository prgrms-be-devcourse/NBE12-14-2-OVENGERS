import type { LoginRequest, LoginResponse, SignupRequest } from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api, { clearTokens, setTokens } from './client';

/** 회원가입. role 은 보내지 않습니다. 서버가 항상 MEMBER 로 생성합니다(FR-AUTH-02). */
export function signup({ email, password, passwordConfirm, nickname }: SignupRequest): Promise<void> {
  return api.post<void>(
    API_ROUTES.auth.signup,
    { email, password, passwordConfirm, nickname },
    { auth: false },
  );
}

export async function login({ email, password }: LoginRequest): Promise<LoginResponse> {
  const data = await api.post<LoginResponse>(
    API_ROUTES.auth.login,
    { email, password },
    { auth: false },
  );
  setTokens({ accessToken: data.accessToken });
  return data;
}

export async function logout(): Promise<void> {
  try {
    await api.post<void>(API_ROUTES.auth.logout, undefined, { auth: false });
  } finally {
    // 서버 폐기에 실패해도 화면은 로그아웃 상태로 만듭니다.
    clearTokens();
  }
}
