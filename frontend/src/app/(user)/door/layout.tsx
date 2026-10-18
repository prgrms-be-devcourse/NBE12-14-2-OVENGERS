import type { ReactNode } from 'react';
import RequireAuth from '@/components/auth/RequireAuth';

export const metadata = { title: '출입하기' };

export default function DoorLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
