import type {
  LoginRequest,
  LoginResponse,
  PasswordResetRequest,
  SignupRequest,
  VerificationCheckResponse,
  VerificationIssueResponse,
  VerificationPurpose,
} from '../types/api';
import { API_ROUTES } from '../constants/apiRoutes';
import api, { clearTokens, setTokens } from './client';

/** 회원가입. role 은 보내지 않습니다. 서버가 항상 MEMBER 로 생성합니다(FR-AUTH-02). */
export function signup({ email, password, passwordConfirm, nickname, grant }: SignupRequest): Promise<void> {
  return api.post<void>(
    API_ROUTES.auth.signup,
    { email, password, passwordConfirm, nickname, grant },
    { auth: false },
  );
}

export function issueVerification(
  email: string,
  purpose: VerificationPurpose,
): Promise<VerificationIssueResponse> {
  return api.post<VerificationIssueResponse>(
    API_ROUTES.auth.verificationIssue,
    { email, purpose },
    { auth: false },
  );
}

export function checkVerification(
  email: string,
  purpose: VerificationPurpose,
  challengeId: number,
  code: string,
): Promise<VerificationCheckResponse> {
  return api.post<VerificationCheckResponse>(
    API_ROUTES.auth.verificationCheck,
    { email, purpose, challengeId, code },
    { auth: false },
  );
}

export function resetPassword(request: PasswordResetRequest): Promise<void> {
  return api.post<void>(API_ROUTES.auth.passwordReset, request, { auth: false });
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

/** 인증된 회원의 탈퇴. 실패 시 현재 세션을 유지한다. */
export function withdraw(currentPassword: string): Promise<void> {
  return api.post<void>(API_ROUTES.auth.withdraw, { currentPassword });
}
