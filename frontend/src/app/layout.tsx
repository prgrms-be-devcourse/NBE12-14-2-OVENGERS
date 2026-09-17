import type { ReactNode } from 'react';
import '../styles/global.css';
import Providers from './providers';

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
    description: '시간을 Slot으로 나누고, 예약한 Slot이 하나의 Key가 됩니다.',
    siteName: 'Slot Key',
    locale: 'ko_KR',
    type: 'website',
  },
};

export const viewport = {
  themeColor: '#0b1220',
  colorScheme: 'dark',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
