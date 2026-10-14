import type { ReactNode } from 'react';
import RequireAuth from '@/components/auth/RequireAuth';

export default function CreditsLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
