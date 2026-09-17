import nextCoreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypeScript from 'eslint-config-next/typescript';

/**
 * next/core-web-vitals + next/typescript 를 그대로 쓰되, react-hooks v7 에서 새로 error 가 된
 * 규칙 두 개는 warn 으로 낮춥니다. 두 규칙이 잡는 코드(HoldCountdown, 관리자 다이얼로그,
 * useApi)는 Next 전환 전부터 있던 것이라 여기서 함께 바꾸지 않습니다. 정리는 별도로 합니다.
 */
const config = [
  { ignores: ['.next/**', 'node_modules/**'] },
  ...nextCoreWebVitals,
  ...nextTypeScript,
  {
    rules: {
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/use-memo': 'warn',
      // 이미지 경로는 백엔드가 내려주는 외부 URL 이라 next/image 최적화 대상이 아닙니다.
      '@next/next/no-img-element': 'off',
    },
  },
];

export default config;
