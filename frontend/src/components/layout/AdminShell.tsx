import type { ReactNode } from 'react';
import Header from './Header';
import AdminSidebar from './AdminSidebar';

export default function AdminShell({ children }: { children: ReactNode }) {
  return (
    <>
      <a className="skip" href="#main">
        본문으로 건너뛰기
      </a>
      <Header />
      <div className="admin-shell">
        <AdminSidebar />
        <main id="main" className="admin-main">
          {children}
        </main>
      </div>
    </>
  );
}
