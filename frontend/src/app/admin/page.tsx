import { redirect } from 'next/navigation';
import { ROUTES } from '@/constants/routePaths';

export default function Page() {
  redirect(ROUTES.adminSpaces);
}
