import { NavLink } from 'react-router-dom';
import { ADMIN_NAV } from '../../constants/routePaths';

export default function AdminSidebar() {
  return (
    <nav className="sidebar" aria-label="관리자 메뉴">
      <p className="eyebrow">관리자</p>
      {ADMIN_NAV.map((item) => (
        <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
