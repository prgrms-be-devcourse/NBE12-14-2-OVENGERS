import RequireAuth from '@/components/auth/RequireAuth';

export const metadata = { title: '모의 출입' };

export default function DoorLayout({ children }) {
  return <RequireAuth>{children}</RequireAuth>;
}
