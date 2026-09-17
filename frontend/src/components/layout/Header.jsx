import { Link, NavLink } from 'react-router-dom';
import { ROUTES, USER_NAV } from '../../constants/routePaths';
import { useAuth } from '../../hooks/useAuth';
import { formatCredit } from '../../utils/price';
import Button from '../common/Button';

export default function Header() {
  const { member, isAuthenticated, isAdmin, logout } = useAuth();

  return (
    <header className="header">
      <Link to={ROUTES.home} className="brand">
        <span className="brandmark" aria-hidden="true">
          🔑
        </span>
        Slot<em>Key</em>
      </Link>

      <nav className="nav" aria-label="주요 메뉴">
        {USER_NAV.filter((item) => !item.requiresAuth || isAuthenticated).map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            {item.label}
          </NavLink>
        ))}
        {isAdmin && (
          <NavLink
            to={ROUTES.adminSpaces}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            관리자
          </NavLink>
        )}
      </nav>

      <div className="header-actions">
        {isAuthenticated ? (
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
            <Link to={ROUTES.login} className="btn small">
              로그인
            </Link>
            <Link to={ROUTES.signup} className="btn small primary">
              회원가입
            </Link>
          </>
        )}
      </div>
    </header>
  );
}
