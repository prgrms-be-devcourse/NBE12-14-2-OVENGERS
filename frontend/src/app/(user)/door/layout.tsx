import type { ReactNode } from 'react';
import RequireAuth from '@/components/auth/RequireAuth';

export const metadata = { title: '모의 출입' };

export default function DoorLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
