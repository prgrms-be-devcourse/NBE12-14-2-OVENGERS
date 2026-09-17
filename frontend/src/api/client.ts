/**
 * HTTP 클라이언트.
 *
 * - 서버 공통 응답 { status, code, message, data } 에서 data 만 벗겨 돌려줍니다.
 * - 오류는 ApiError 로 통일하여 화면이 error.code 로 분기할 수 있게 합니다.
 * - 401 을 받으면 Refresh Token 으로 한 번만 재발급을 시도하고, 실패하면 로그아웃 처리합니다.
 */

import { API_BASE_URL, API_ROUTES } from '../constants/apiRoutes';
import { ERROR_CODE } from '../constants/errorCodes';
import type { AuthTokens } from '../types/api';
import { STORAGE_KEYS, getItem, removeItem, setItem } from '../utils/storage';

/** 서버 공통 응답 봉투 */
interface Envelope<T> {
  status?: number;
  code?: string;
  message?: string;
  data?: T;
}

export interface ApiErrorInit {
  code?: string;
  status: number;
  message?: string;
  data?: unknown;
}

export class ApiError extends Error {
  readonly code?: string;
  readonly status: number;
  readonly data: unknown;

  constructor({ code, status, message, data = null }: ApiErrorInit) {
    super(message ?? code ?? 'API_ERROR');
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.data = data;
  }
}

/* ------------------------------------------------------------------ 토큰 */

let accessToken: string | null = getItem(STORAGE_KEYS.accessToken);
let refreshToken: string | null = getItem(STORAGE_KEYS.refreshToken);
let onUnauthorized: (() => void) | null = null;

export function setTokens(tokens: Partial<AuthTokens> | null): void {
  accessToken = tokens?.accessToken ?? null;
  refreshToken = tokens?.refreshToken ?? null;
  if (accessToken) setItem(STORAGE_KEYS.accessToken, accessToken);
  else removeItem(STORAGE_KEYS.accessToken);
  if (refreshToken) setItem(STORAGE_KEYS.refreshToken, refreshToken);
  else removeItem(STORAGE_KEYS.refreshToken);
}

export function clearTokens(): void {
  setTokens(null);
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getRefreshToken(): string | null {
  return refreshToken;
}

export function hasSession(): boolean {
  return Boolean(accessToken);
}

/** AuthContext 가 등록합니다. 재발급까지 실패했을 때 호출됩니다. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

/* ---------------------------------------------------------------- 요청 */

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue>;

export interface RequestOptions {
  query?: QueryParams;
  headers?: Record<string, string>;
  /** false 면 Authorization 헤더를 붙이지 않고 401 재발급도 시도하지 않습니다. */
  auth?: boolean;
}

interface SendOptions extends RequestOptions {
  body?: unknown;
}

function buildUrl(path: string, query?: QueryParams): string {
  // API_BASE_URL 이 '/api/v1' 같은 상대 경로일 수 있으므로 기준 오리진이 필요합니다.
  // 실제 호출은 모두 브라우저에서 일어나지만, 서버 렌더 중 평가돼도 터지지 않게 둡니다.
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const url = new URL(`${API_BASE_URL}${path}`, origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

async function parseBody<T>(response: Response): Promise<Envelope<T> | null> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as Envelope<T>;
  } catch {
    return null;
  }
}

async function send<T>(
  method: string,
  path: string,
  { body, query, headers, auth = true }: SendOptions = {},
): Promise<T> {
  const requestHeaders: Record<string, string> = { Accept: 'application/json', ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (auth && accessToken) requestHeaders.Authorization = `Bearer ${accessToken}`;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    throw new ApiError({ code: ERROR_CODE.NETWORK_ERROR, status: 0, message });
  }

  const payload = await parseBody<T>(response);

  if (!response.ok) {
    throw new ApiError({
      code: payload?.code,
      status: response.status,
      message: payload?.message,
      data: payload?.data ?? null,
    });
  }
  // 본문이 없는 성공 응답(204 등)도 있으므로 호출부가 기대하는 타입으로 맞춰 돌려줍니다.
  return (payload?.data ?? null) as T;
}

/* ------------------------------------------------------- 토큰 재발급 */

let refreshPromise: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshToken) return false;
  // 동시에 여러 요청이 401 을 받아도 재발급은 한 번만 수행합니다.
  if (!refreshPromise) {
    refreshPromise = send<AuthTokens>('POST', API_ROUTES.auth.refresh, {
      body: { refreshToken },
      auth: false,
    })
      .then((data) => {
        setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
        return true;
      })
      .catch(() => {
        clearTokens();
        return false;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

async function request<T>(method: string, path: string, options: SendOptions = {}): Promise<T> {
  try {
    return await send<T>(method, path, options);
  } catch (error) {
    const canRetry = error instanceof ApiError && error.status === 401 && options.auth !== false;
    if (!canRetry) throw error;

    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      onUnauthorized?.();
      throw error;
    }
    return send<T>(method, path, options);
  }
}

export const api = {
  get: <T>(path: string, options?: RequestOptions): Promise<T> => request<T>('GET', path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('POST', path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('PUT', path, { ...options, body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>('PATCH', path, { ...options, body }),
  delete: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>('DELETE', path, options),
};

export default api;
