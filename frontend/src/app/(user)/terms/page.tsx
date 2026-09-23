import { BOOKING_TERMS } from '@/constants/bookingTerms';
export const metadata = { title: '약관 및 이용 규정' };
export default function TermsPage() {
  return <div className="public-terms"><h1>약관 및 이용 규정</h1><p>Slot Key의 예약·결제와 공간 이용 기준을 확인하세요.</p>
    <nav aria-label="규정 목차">{BOOKING_TERMS.map(term => <a key={term.id} href={`#${term.id}`}>{term.title}</a>)}</nav>
    {BOOKING_TERMS.map(term => <article key={term.id} id={term.id}><h2>{term.title}</h2><p>버전 {term.version}</p>
      {term.sections.map(section => <section key={section.title}><h3>{section.title}</h3>{section.paragraphs.map((paragraph,index) => <p key={index}>{paragraph}</p>)}</section>)}
    </article>)}
  </div>;
}
