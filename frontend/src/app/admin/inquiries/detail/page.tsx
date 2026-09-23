import { Suspense } from 'react';
import InquiryDetailPage from '@/views/inquiry/InquiryDetailPage';
import LoadingSpinner from '@/components/common/LoadingSpinner';
export const metadata = { title: '문의 상세' };
export default function Page() { return <Suspense fallback={<LoadingSpinner />}><InquiryDetailPage admin /></Suspense>; }
