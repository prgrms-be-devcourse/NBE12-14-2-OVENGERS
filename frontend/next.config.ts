import type { NextConfig } from 'next';

/**
 * Next.js 설정.
 *
 * 프론트엔드는 언제나 같은 오리진의 `/api/v1` 로 요청합니다. 브라우저가 백엔드 주소를
 * 직접 알 필요가 없으므로 CORS 설정과 혼합 콘텐츠(HTTPS 페이지 → HTTP API) 문제를
 * 함께 피합니다. 같은 오리진으로 보내는 일은 실행 환경에 따라 다른 곳이 맡습니다.
 *
 * - 개발/Vercel: 아래 rewrites 가 백엔드로 넘깁니다. 기본값은 로컬 백엔드이므로
 *   npm run dev 만으로 바로 붙습니다. 배포 환경에서는 BACKEND_ORIGIN 을 실제 백엔드
 *   주소로 넣어 줘야 합니다.
 * - S3 + CloudFront: STATIC_EXPORT=true npm run build 로 정적 파일(out/)을 만듭니다.
 *   rewrites 는 정적 export 와 함께 쓸 수 없으므로, CloudFront 의 `/api/*` 동작이
 *   EC2 오리진으로 넘겨야 합니다.
 */
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';
const STATIC_EXPORT = process.env.STATIC_EXPORT === 'true';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Next 가 frontend/ 에 AGENTS.md·CLAUDE.md 를 자동 생성하지 않게 합니다.
  // 이 저장소의 규칙 문서는 루트 CLAUDE.md 하나로 유지합니다.
  agentRules: false,
  ...(STATIC_EXPORT
      ? {
        output: 'export' as const,
        images: { unoptimized: true },
      }
      : {
        async rewrites() {
          return [
            {
              source: '/api/v1/:path*',
              destination: `${BACKEND_ORIGIN}/api/v1/:path*`,
            },
          ];
        },
      }),
};

export default nextConfig;