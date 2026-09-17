/**
 * Next.js 설정.
 *
 * 프론트엔드는 언제나 같은 오리진의 `/api/v1` 로 요청하고, 실제 백엔드로 넘기는 일은
 * 여기 rewrite 가 맡습니다. 브라우저가 백엔드 주소를 직접 알 필요가 없으므로
 * CORS 설정과 혼합 콘텐츠(HTTPS 페이지 → HTTP API) 문제를 함께 피합니다.
 * (기존 vercel.json 의 rewrites 를 옮겨온 것입니다.)
 *
 * 로컬 백엔드로 붙일 때는 .env.local 에 BACKEND_ORIGIN=http://localhost:8080 을 둡니다.
 */
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? 'http://3.36.74.44:8080';

const nextConfig = {
  reactStrictMode: true,
  // Next 가 frontend/ 에 AGENTS.md·CLAUDE.md 를 자동 생성하지 않게 합니다.
  // 이 저장소의 규칙 문서는 루트 CLAUDE.md 하나로 유지합니다.
  agentRules: false,
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${BACKEND_ORIGIN}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
