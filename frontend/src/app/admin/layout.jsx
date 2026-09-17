import RequireAdmin from '@/components/auth/RequireAdmin';
import AdminShell from '@/components/layout/AdminShell';

export const metadata = { title: { default: '관리자', template: '%s · 관리자 · Slot Key' } };

/**
 * 관리자 화면 전체를 감쌉니다.
 * 메뉴와 화면을 가리는 것은 편의일 뿐이고, 관리자 권한 판단은 서버가 매 요청마다 합니다.
 */
export default function AdminLayout({ children }) {
  return (
    <RequireAdmin>
      <AdminShell>{children}</AdminShell>
    </RequireAdmin>
  );
}
