import { issueDoorToken } from '../api/doorAccessApi';
import type { IssuedDoorToken } from '../types/api';

const prefix = 'slotkey:reservation-key:v1:';
const pending = new Map<string, Promise<IssuedDoorToken>>();
const memory = new Map<string, IssuedDoorToken>();
const versions = new Map<string, number>();

export const reservationKeyStorageId = (memberId: number, reservationId: number | string) =>
  `${prefix}${memberId}:${reservationId}`;

export function reservationEndTime(value: string): number {
  return new Date(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}+09:00`).getTime();
}

export function forgetReservationKey(memberId: number, reservationId: number | string) {
  const id = reservationKeyStorageId(memberId, reservationId);
  versions.set(id, (versions.get(id) ?? 0) + 1);
  memory.delete(id);
  try { window.localStorage.removeItem(id); window.localStorage.removeItem(`${id}:visible`); } catch { /* 화면에서는 항상 숨긴다. */ }
}

export function readReservationKey(memberId: number, reservationId: number | string): IssuedDoorToken | null {
  const id = reservationKeyStorageId(memberId, reservationId);
  const cached = memory.get(id);
  if (cached) return cached;
  const raw = window.localStorage.getItem(id);
  if (!raw) return null;
  const data = JSON.parse(raw) as IssuedDoorToken;
  if (Number(data.reservationId) !== Number(reservationId) || typeof data.token !== 'string' || !data.token || typeof data.issuedAt !== 'string') {
    throw new Error('저장된 출입 키를 확인할 수 없습니다. 관리자에게 문의해 주세요.');
  }
  return data;
}

/** 서버의 원문 재조회 대신, 회원·예약별로 같은 브라우저에 발급 결과를 보관한다. */
export function ensureReservationKey(memberId: number, reservationId: number | string, endTime: string): Promise<IssuedDoorToken> {
  const id = reservationKeyStorageId(memberId, reservationId);
  const existing = pending.get(id);
  if (existing) return existing;
  const version = versions.get(id) ?? 0;
  const issueOnce = async () => {
    if (!Number.isFinite(reservationEndTime(endTime)) || Date.now() >= reservationEndTime(endTime)) {
      throw new Error('이용 시간이 종료되어 출입 키를 사용할 수 없습니다.');
    }
    const cached = readReservationKey(memberId, reservationId);
    if (cached) return cached;
    // 원문을 보관할 수 없는 환경에서는 자동 발급부터 하지 않는다.
    const probe = `${id}:probe`;
    try {
      window.localStorage.setItem(probe, '1');
      window.localStorage.removeItem(probe);
    } catch {
      throw new Error('출입 키를 보관하려면 브라우저의 사이트 저장소를 허용해 주세요.');
    }
    const key = await issueDoorToken(reservationId);
    if (version !== (versions.get(id) ?? 0) || Date.now() >= reservationEndTime(endTime)) {
      throw new Error('예약 상태가 변경되었습니다. 예약을 다시 확인해 주세요.');
    }
    memory.set(id, key);
    // 발급 후 저장 실패에도 원문을 현재 화면에서 잃지 않는다.
    try { window.localStorage.setItem(id, JSON.stringify(key)); } catch { /* 아래 화면에서 보관 실패 안내 */ }
    return key;
  };
  // Strict Mode·동시 렌더와 여러 탭의 중복 발급을 방지한다.
  const request = (navigator.locks
    ? navigator.locks.request(id, issueOnce)
    : issueOnce()).finally(() => pending.delete(id));
  pending.set(id, request);
  return request;
}
