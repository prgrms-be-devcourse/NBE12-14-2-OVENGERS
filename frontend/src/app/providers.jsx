'use client';

/** 전역 클라이언트 컨텍스트. 루트 레이아웃은 서버 컴포넌트로 두기 위해 분리했습니다. */

import { AuthProvider } from '../context/AuthProvider';

export default function Providers({ children }) {
  return <AuthProvider>{children}</AuthProvider>;
}
