'use client';

import Link from 'next/link';
import { ROUTES, USER_NAV } from '../../constants/routePaths';
import { useAuth } from '../../hooks/useAuth';
import { formatCredit } from '../../utils/price';
import BrandMark from '../brand/BrandMark';
import Button from '../common/Button';
import ActiveLink from './ActiveLink';

export default function Header() {
  const { member, isAdmin, logout } = useAuth();

  return (
    <header className="header">
      <Link href={ROUTES.home} className="brand">
        <BrandMark className="brandmark" size={40} />
        Slot<em>Key</em>
      </Link>

      <nav className="nav" aria-label="주요 메뉴">
        {USER_NAV.filter((item) => !item.requiresAuth || member).map((item) => (
          <ActiveLink key={item.href} href={item.href}>
            {item.label}
          </ActiveLink>
        ))}
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
    </header>
  );
}
