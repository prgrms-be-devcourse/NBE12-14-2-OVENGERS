import type { ReactNode } from 'react';
import UserShell from '@/components/layout/UserShell';

export default function UserRouteLayout({ children }: { children: ReactNode }) {
  return <UserShell>{children}</UserShell>;
}
