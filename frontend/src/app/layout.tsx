import type { ReactNode } from 'react';
import '../styles/global.css';
import '../styles/home-shell.css';
import Providers from './providers';
import Footer from '../components/layout/Footer';
import '../styles/site-footer.css';

export const metadata = {
  title: {
    default: 'Slot Key',
    template: '%s · Slot Key',
  },
  description:
    '회의실과 공유오피스를 30분 단위로 예약하고, 예약 상태와 이용 시간에 맞춰 출입 권한을 받습니다.',
  applicationName: 'Slot Key',
  openGraph: {
    title: 'Slot Key',
    description: '몰입할 공간, 필요한 만큼. 회의실과 공유오피스를 만나는 Slot Key.',
    siteName: 'Slot Key',
    locale: 'ko_KR',
    type: 'website',
  },
};

export const viewport = {
  themeColor: '#f1f6fb',
  colorScheme: 'light',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
        <Footer />
      </body>
    </html>
  );
}
