import Link from 'next/link';
import BrandMark from '../brand/BrandMark';

export default function Footer() {
  return (
    <footer className="footer">
      <Link href="/" className="footer-brand"><BrandMark size={30} /> <span>Slot Key</span></Link>
      <span>현재 학습용으로 운영되며, 실제 결제 및 출입 연동은<br />향후 업데이트 시 안내드리겠습니다.</span>
    </footer>
  );
}
