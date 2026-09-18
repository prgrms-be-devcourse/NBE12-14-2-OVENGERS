'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { ROUTES, USER_NAV } from '../../constants/routePaths';
import { useAuth } from '../../hooks/useAuth';
import { formatCredit } from '../../utils/price';
import BrandMark from '../brand/BrandMark';
import Button from '../common/Button';
import ActiveLink from './ActiveLink';

export default function Header() {
  const { member, isAdmin, logout } = useAuth();
  const pathname = usePathname();
  const isHome = pathname === '/';
  const hideMyReservations = isAdmin || pathname === '/admin' || pathname.startsWith('/admin/');
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const update = () => setScrolled(window.scrollY > 48);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);

  return (
    <header className={`header${isHome && scrolled ? ' header-scrolled' : ''}`}>
      <Link href={ROUTES.home} className="brand">
        <BrandMark className="brandmark" size={40} />
        <span>Slot <em>Key</em></span>
      </Link>

      <nav className="nav" aria-label="주요 메뉴">
        {USER_NAV.filter((item) =>
          (!item.requiresAuth || member) &&
          !(hideMyReservations && item.href === ROUTES.reservations)
        ).map((item) => (
          <ActiveLink key={item.href} href={item.href}>
            {item.label}
          </ActiveLink>
        ))}
        <Link href="/#story">스토리</Link>
        {isAdmin && <ActiveLink href={ROUTES.adminSpaces}>관리자</ActiveLink>}
      </nav>

      <div className="header-actions">
        {member ? (
          <>
            <span className="muted">{member.nickname}님</span>
            {!isAdmin && (
              <span className="badge cyan" title="크레딧은 1크레딧 = 1원, 예약 결제에만 사용됩니다.">
                {formatCredit(member.balance)}
              </span>
            )}
            <Button size="small" onClick={logout}>
              로그아웃
            </Button>
          </>
        ) : (
          <>
            <Link href={ROUTES.login} className="btn small">
              로그인
            </Link>
            <Link href={ROUTES.signup} className="btn small primary">
              회원가입
            </Link>
          </>
        )}
      </div>
      <details className="home-account-menu">
        <summary>계정</summary>
        <div className="home-account-panel">
          <Link href={ROUTES.spaces}>오피스 찾기</Link>
          <Link href="/#story">스토리</Link>
          <Link href={ROUTES.door}>모의 출입</Link>
          {member ? <>
            <span>{member.nickname}님</span>
            {!isAdmin && <span>{formatCredit(member.balance)}</span>}
            {!hideMyReservations && <Link href={ROUTES.reservations}>내 예약</Link>}
            {isAdmin && <Link href={ROUTES.adminSpaces}>관리자</Link>}
            <button onClick={logout}>로그아웃</button>
          </> : <><Link href={ROUTES.login}>로그인</Link><Link href={ROUTES.signup}>회원가입</Link></>}
        </div>
      </details>
    </header>
  );
}
