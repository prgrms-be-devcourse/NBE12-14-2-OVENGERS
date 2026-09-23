'use client';

import type { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import Header from './Header';

export default function UserShell({ children }: { children: ReactNode }) {
  const isHome = usePathname() === '/';
  return (
    <div className={isHome ? 'home-shell' : 'user-shell'}>
      <a className="skip" href="#main">본문으로 건너뛰기</a>
      <Header />
      <main id="main" className={isHome ? 'home-main' : 'container'}>{children}</main>
    </div>
  );
}
