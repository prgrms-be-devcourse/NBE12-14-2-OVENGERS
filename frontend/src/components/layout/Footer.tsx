import Link from 'next/link';
import BrandMark from '../brand/BrandMark';
import { BOOKING_TERMS } from '../../constants/bookingTerms';

export default function Footer() {
  return <footer className="site-footer">
    <div className="site-footer-inner">
      <div className="site-footer-top">
        <div className="site-footer-intro">
          <Link href="/" className="footer-brand"><BrandMark size={30} /><span>Slot Key</span></Link>
          <p>몰입할 공간, 필요한 만큼.<br />공간 탐색부터 예약과 출입까지.</p>
        </div>
        <nav aria-label="서비스 안내"><h2>서비스</h2>
          <Link href="/spaces">오피스 찾기</Link><Link href="/door">모의 출입</Link><Link href="/#story">Slot Key 이야기</Link>
        </nav>
        <nav aria-label="이용 지원"><h2>이용 지원</h2>
          <Link href="/reservations">내 예약</Link><Link href="/inquiries">내 문의</Link><Link href="/inquiries/new">문의하기</Link>
        </nav>
        <nav aria-label="약관 및 이용 규정"><h2>약관 및 이용 규정</h2>
          {BOOKING_TERMS.map(term => <Link key={term.id} href={`/terms#${term.id}`}>{term.title}</Link>)}
        </nav>
      </div>
      <div className="site-footer-bottom"><span>© {new Date().getFullYear()} Slot Key</span><Link href="/terms">이용 규정 전체 보기</Link><span className="site-footer-locale">한국어 · 대한민국 표준시(KST)</span></div>
      <p className="site-footer-notice">Slot Key는 학습용 프로젝트입니다. 예약 결제는 모의 크레딧으로 진행되며 실제 금액이 청구되지 않습니다. 출입 기능은 모의 환경으로 제공되며 실제 건물 출입과 연동되지 않습니다.</p>
    </div>
  </footer>;
}
