import { createContext } from 'react';

/**
 * 로그인 상태 컨텍스트.
 *
 * Provider 는 AuthProvider.jsx 에 있습니다.
 * 컴포넌트가 아닌 값(context 객체)을 컴포넌트 파일에서 함께 내보내면
 * Fast Refresh 가 동작하지 않아 파일을 나눴습니다.
 */
export const AuthContext = createContext(null);
