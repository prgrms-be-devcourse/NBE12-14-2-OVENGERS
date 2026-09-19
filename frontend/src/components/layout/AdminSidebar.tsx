import { ADMIN_NAV } from '../../constants/routePaths';
import ActiveLink from './ActiveLink';

export default function AdminSidebar() {
  return (
    <nav className="sidebar" aria-label="관리자 메뉴">
      <p className="eyebrow">관리자</p>
      {ADMIN_NAV.map((item) => (
        <ActiveLink key={item.href} href={item.href}>
          {item.label}
        </ActiveLink>
      ))}
    </nav>
  );
}
