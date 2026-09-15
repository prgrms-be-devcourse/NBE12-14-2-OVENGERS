/**
 * 결제 확인(POST /reservations/{id}/pay) 요청용 멱등성 키.
 *
 * 돈이 움직이는 지점은 결제 확인이므로 키도 이 단계에만 붙인다
 * (core-domain-decisions.md 2-1). 같은 HOLD에 대한 결제 시도 동안에는 키를 유지해야
 * 네트워크 오류로 재시도할 때 서버가 중복 차감을 막을 수 있다.
 * 결제가 성공하거나 예약이 만료되어 다시 예약해야 하면 renew() 로 새 키를 만든다.
 */

import { useCallback, useState } from 'react';

function createKey() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  // randomUUID 미지원 브라우저에서도 서버의 UUID 형식 검증을 통과한다.
  const bytes = window.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function useIdempotencyKey() {
  const [key, setKey] = useState(createKey);
  const renew = useCallback(() => {
    const next = createKey();
    setKey(next);
    return next;
  }, []);
  return { key, renew };
}

export default useIdempotencyKey;
