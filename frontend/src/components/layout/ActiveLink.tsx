'use client';

/** 현재 경로와 일치하면 active 클래스를 붙이는 링크(react-router 의 NavLink 대체). */

import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export interface ActiveLinkProps {
  href: string;
  /** true 면 하위 경로를 활성으로 보지 않습니다. */
  exact?: boolean;
  children: ReactNode;
}

export default function ActiveLink({ href, exact = false, children, ...rest }: ActiveLinkProps) {
  const pathname = usePathname();
  const active = exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);

  return (
    <Link href={href} className={active ? 'active' : undefined} aria-current={active ? 'page' : undefined} {...rest}>
      {children}
    </Link>
  );
}
