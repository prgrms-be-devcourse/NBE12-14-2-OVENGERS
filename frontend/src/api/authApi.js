import api, { clearTokens, getRefreshToken, setTokens } from './client';
import { API_ROUTES } from '../constants/apiRoutes';

/** 회원가입. role 은 보내지 않습니다. 서버가 항상 MEMBER 로 생성합니다(FR-AUTH-02). */
export function signup({ email, password, nickname }) {
  return api.post(API_ROUTES.auth.signup, { email, password, nickname }, { auth: false });
}

export async function login({ email, password }) {
  const data = await api.post(API_ROUTES.auth.login, { email, password }, { auth: false });
  setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
  return data;
}

export async function logout() {
  const refreshToken = getRefreshToken();
  try {
    if (refreshToken) await api.post(API_ROUTES.auth.logout, { refreshToken });
  } finally {
    // 서버 폐기에 실패해도 화면은 로그아웃 상태로 만듭니다.
    clearTokens();
  }
}
