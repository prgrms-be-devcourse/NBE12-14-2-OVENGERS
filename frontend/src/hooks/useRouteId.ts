'use client';

import { useParams, usePathname } from 'next/navigation';

/** 정적 export(S3 + CloudFront) 빌드에서 동적 세그먼트에 들어가는 자리표시자입니다. */
const PLACEHOLDER = '_';

const SEGMENT_ANCHOR = {
  spaceId: 'spaces',
  reservationId: 'reservations',
} as const;

/**
 * 경로의 동적 세그먼트(공간/예약 ID)를 읽습니다.
 *
 * 서버 실행(next start, Vercel)에서는 useParams 값을 그대로 씁니다. 정적 export 에서는
 * CDN 이 `/spaces/123` 요청에 자리표시자 페이지(`/spaces/_.html`)를 내려주므로
 * useParams 가 '_' 를 돌려줍니다. 이때만 브라우저가 실제로 연 경로에서 ID 를 꺼냅니다.
 */
export function useRouteId(key: keyof typeof SEGMENT_ANCHOR): string {
  const params = useParams<Record<string, string>>();
  const pathname = usePathname();
  const value = params[key];
  if (value !== PLACEHOLDER) return value;

  const segments = pathname.split('/').filter(Boolean);
  const index = segments.indexOf(SEGMENT_ANCHOR[key]);
  return index >= 0 && segments[index + 1] ? segments[index + 1] : value;
}

export default useRouteId;
