/**
 * HTTP 클라이언트.
 *
 * - 서버 공통 응답 { status, code, message, data } 에서 data 만 벗겨 돌려줍니다.
 * - 오류는 ApiError 로 통일하여 화면이 error.code 로 분기할 수 있게 합니다.
 * - 401 을 받으면 Refresh Token 으로 한 번만 재발급을 시도하고, 실패하면 로그아웃 처리합니다.
 */

import { API_BASE_URL, API_ROUTES } from '../constants/apiRoutes';
import { ERROR_CODE } from '../constants/errorCodes';
import { STORAGE_KEYS, getItem, removeItem, setItem } from '../utils/storage';

export class ApiError extends Error {
  constructor({ code, status, message, data = null }) {
    super(message ?? code ?? 'API_ERROR');
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.data = data;
  }
}

/* ------------------------------------------------------------------ 토큰 */

let accessToken = getItem(STORAGE_KEYS.accessToken);
let refreshToken = getItem(STORAGE_KEYS.refreshToken);
let onUnauthorized = null;

export function setTokens(tokens) {
  accessToken = tokens?.accessToken ?? null;
  refreshToken = tokens?.refreshToken ?? null;
  if (accessToken) setItem(STORAGE_KEYS.accessToken, accessToken);
  else removeItem(STORAGE_KEYS.accessToken);
  if (refreshToken) setItem(STORAGE_KEYS.refreshToken, refreshToken);
  else removeItem(STORAGE_KEYS.refreshToken);
}

export function clearTokens() {
  setTokens(null);
}

export function getAccessToken() {
  return accessToken;
}

export function getRefreshToken() {
  return refreshToken;
}

export function hasSession() {
  return Boolean(accessToken);
}

/** AuthContext 가 등록합니다. 재발급까지 실패했을 때 호출됩니다. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

/* ---------------------------------------------------------------- 요청 */

function buildUrl(path, query) {
  // API_BASE_URL 이 '/api/v1' 같은 상대 경로일 수 있으므로 기준 오리진이 필요합니다.
  // 실제 호출은 모두 브라우저에서 일어나지만, 서버 렌더 중 평가돼도 터지지 않게 둡니다.
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const url = new URL(`${API_BASE_URL}${path}`, origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      url.searchParams.set(key, value);
    });
  }
  return url.toString();
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function send(method, path, { body, query, headers, auth = true } = {}) {
  const requestHeaders = { Accept: 'application/json', ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (auth && accessToken) requestHeaders.Authorization = `Bearer ${accessToken}`;

  let response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    throw new ApiError({ code: ERROR_CODE.NETWORK_ERROR, status: 0, message: cause.message });
  }

  const payload = await parseBody(response);

  if (!response.ok) {
    throw new ApiError({
      code: payload?.code,
      status: response.status,
      message: payload?.message,
      data: payload?.data ?? null,
    });
  }
  return payload?.data ?? null;
}

/* ------------------------------------------------------- 토큰 재발급 */

let refreshPromise = null;

async function refreshAccessToken() {
  if (!refreshToken) return false;
  // 동시에 여러 요청이 401 을 받아도 재발급은 한 번만 수행합니다.
  if (!refreshPromise) {
    refreshPromise = send('POST', API_ROUTES.auth.refresh, {
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

async function request(method, path, options = {}) {
  try {
    return await send(method, path, options);
  } catch (error) {
    const canRetry = error instanceof ApiError && error.status === 401 && options.auth !== false;
    if (!canRetry) throw error;

    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      onUnauthorized?.();
      throw error;
    }
    return send(method, path, options);
  }
}

export const api = {
  get: (path, options) => request('GET', path, options),
  post: (path, body, options) => request('POST', path, { ...options, body }),
  put: (path, body, options) => request('PUT', path, { ...options, body }),
  patch: (path, body, options) => request('PATCH', path, { ...options, body }),
  delete: (path, options) => request('DELETE', path, options),
};

export default api;
