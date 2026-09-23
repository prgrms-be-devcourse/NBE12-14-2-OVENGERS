'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { BOOKING_TERMS, hasAllBookingAgreements } from '../../constants/bookingTerms';
import type { BookingTerm } from '../../constants/bookingTerms';
import Button from '../common/Button';

export default function TermsAgreement({ acceptedIds, onChange, disabled }: {
  acceptedIds: string[];
  onChange: (ids: string[]) => void;
  disabled?: boolean;
}) {
  const id = useId();
  const [activeId, setActiveId] = useState<string | null>(null);
  const count = BOOKING_TERMS.filter((term) => acceptedIds.includes(term.id)).length;
  return <section className="payment-terms" aria-labelledby={`${id}-section`}>
    <div className="terms-summary-heading">
      <h3 id={`${id}-section`}>약관 동의</h3>
      <span role="status">{count}/{BOOKING_TERMS.length} 동의 완료</span>
    </div>
    <div className="terms-list">
      {BOOKING_TERMS.map((term, index) => {
        const remaining = [...BOOKING_TERMS.slice(index + 1), ...BOOKING_TERMS.slice(0, index)]
          .find((candidate) => !acceptedIds.includes(candidate.id));
        return <AgreementItem key={`${term.id}:${term.version}`} term={term}
          step={index + 1} opened={activeId === term.id}
          onOpen={() => setActiveId(term.id)} onClose={() => setActiveId(null)}
          hasNext={Boolean(remaining)}
          onComplete={() => {
            if (!disabled && acceptedIds.includes(term.id)) setActiveId(remaining?.id ?? null);
          }}
          checked={acceptedIds.includes(term.id)} disabled={disabled}
          onChange={(checked) => onChange(checked
            ? [...new Set([...acceptedIds, term.id])]
            : acceptedIds.filter((accepted) => accepted !== term.id))} />;
      })}
    </div>
    {!hasAllBookingAgreements(acceptedIds) && <p className="form-help">필수 약관 3개에 모두 동의하면 결제할 수 있습니다.</p>}
  </section>;
}

function AgreementItem({ term, checked, onChange, disabled, opened, onOpen, onClose, onComplete, hasNext, step }: {
  opened: boolean;
  onOpen: () => void;
  onClose: () => void;
  onComplete: () => void;
  hasNext: boolean;
  step: number;
  term: BookingTerm;
  checked: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  const [readToEnd, setReadToEnd] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const id = useId();
  const checkEnd = () => {
    const content = contentRef.current;
    if (content && content.clientHeight > 0 && content.scrollHeight - content.scrollTop - content.clientHeight <= 2) {
      setReadToEnd(true);
    }
  };

  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = triggerRef.current;
    const content = contentRef.current;
    if (!opened || !dialog || !content) return;
    dialog.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    content.scrollTop = 0;
    content.focus();
    const frame = requestAnimationFrame(checkEnd);
    const observer = new ResizeObserver(checkEnd);
    observer.observe(content);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      dialog.close();
      document.body.style.overflow = previousOverflow;
      trigger?.focus();
    };
  }, [opened]);

  return (
    <>
      <button ref={triggerRef} type="button" className="terms-summary-button"
        disabled={disabled} aria-haspopup="dialog" aria-controls={`${id}-dialog`}
        onClick={onOpen}>
        <span><strong>[필수] {term.title}</strong><small>{checked ? '동의 완료 · 다시 보기' : term.description}</small></span>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
      </button>
      <dialog ref={dialogRef} id={`${id}-dialog`} className="terms-dialog"
        aria-labelledby={`${id}-title`} onCancel={(event) => { event.preventDefault(); onClose(); }}>
        <header className="terms-dialog-header">
          <div><h2 id={`${id}-title`}>{term.title}</h2><p>{step}/{BOOKING_TERMS.length} · 내용을 끝까지 확인한 뒤 동의해 주세요.</p></div>
          <Button aria-label="약관 닫기" onClick={onClose}>닫기</Button>
        </header>
        <div ref={contentRef} className="terms-dialog-content" tabIndex={0}
          role="region" aria-label="약관 본문" aria-describedby={`${id}-help`} onScroll={checkEnd}>
          <p className="terms-version">약관 버전 {term.version}</p>
          {term.sections.map((section) => <section key={section.title}>
            <h4>{section.title}</h4>
            {section.paragraphs.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
          </section>)}
        </div>
        <footer className="terms-dialog-footer">
          <p id={`${id}-help`} className="form-help" role="status">
            {checked ? '이 약관에 동의했습니다.' : readToEnd ? '마지막까지 확인했습니다. 아래 항목에 동의해 주세요.' : '본문을 맨 아래까지 내리면 동의할 수 있습니다.'}
          </p>
          <div className="terms-dialog-actions">
            <label className="check">
              <input type="checkbox" checked={checked} disabled={disabled || !readToEnd}
                onChange={(event) => onChange(event.target.checked)} />
              <span>{term.title}에 동의합니다.</span>
            </label>
            <Button variant="primary" disabled={disabled || !checked} onClick={onComplete}>
              {hasNext ? '동의하고 다음' : '동의 완료'}
            </Button>
          </div>
        </footer>
      </dialog>
    </>
  );
}
