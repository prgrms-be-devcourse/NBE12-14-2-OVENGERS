/**
 * localStorage 접근 래퍼.
 * 시크릿 모드나 사이트 데이터 차단 설정에서는 접근 자체가 예외를 던지므로
 * 모든 호출을 try/catch 로 감싸고, 실패 시 null 을 돌려주어 화면이 계속 동작하게 합니다.
 */

const PREFIX = 'slotkey:';

export const STORAGE_KEYS = {
  accessToken: `${PREFIX}accessToken`,
  refreshToken: `${PREFIX}refreshToken`,
  lastSpaceFilter: `${PREFIX}lastSpaceFilter`,
};

export function getItem(key) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setItem(key, value) {
  try {
    window.localStorage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}

export function removeItem(key) {
  try {
    window.localStorage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}

export function getJson(key, fallback = null) {
  const raw = getItem(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

export function setJson(key, value) {
  return setItem(key, JSON.stringify(value));
}
