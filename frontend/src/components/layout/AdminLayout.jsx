import { Outlet } from 'react-router-dom';
import Header from './Header';
import AdminSidebar from './AdminSidebar';

export default function AdminLayout() {
  return (
    <>
      <a className="skip" href="#main">
        본문으로 건너뛰기
      </a>
      <Header />
      <div className="admin-shell">
        <AdminSidebar />
        <main id="main" className="admin-main">
          <Outlet />
        </main>
      </div>
    </>
  );
}
