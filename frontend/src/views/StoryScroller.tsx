'use client';

import { Children, useEffect, useRef, useState } from 'react';
import type { PointerEvent, ReactNode } from 'react';
import styles from './HomePage.module.css';

const chapters = [
  { id: 'story-space', number: '01', label: '공간' },
  { id: 'story-time', number: '02', label: '시간' },
  { id: 'story-connection', number: '03', label: '연결' },
];

/** 세로 스크롤 거리만큼 가로 이동. 마지막 패널 이후에는 기본 문서 스크롤로 이어진다. */
export default function StoryScroller({ children }: { children: ReactNode }) {
  const root = useRef<HTMLDivElement>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const panels = useRef<Array<HTMLDivElement | null>>([]);
  const geometry = useRef({ start: 0, distance: 0, width: 0, pinned: false });
  const drag = useRef<{ id: number; x: number; y: number; scroll: number; horizontal: boolean } | null>(null);
  const [pinned, setPinned] = useState(false);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const section = root.current;
    const windowElement = viewport.current;
    const strip = track.current;
    if (!section || !windowElement || !strip) return;
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0;

    const update = () => {
      frame = 0;
      const { start, distance, width, pinned: isPinned } = geometry.current;
      if (!isPinned) return;
      const offset = Math.max(0, Math.min(distance, window.scrollY - start));
      strip.style.transform = `translate3d(${-offset}px, 0, 0)`;
      setActive(Math.min(chapters.length - 1, Math.round(offset / width)));
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    const measure = () => {
      const top = window.innerWidth <= 850 ? 72 : 88;
      const availableHeight = window.innerHeight - top;
      const width = windowElement.clientWidth;
      // 확대·작은 높이에서는 내용을 잘라내지 않고 일반 세로 문서로 제공한다.
      const contentHeight = Math.max(...panels.current.map((panel) => panel?.scrollHeight ?? 0));
      const canPin = !motion.matches && window.innerWidth >= 900 && availableHeight >= Math.max(580, contentHeight);
      const distance = width * (chapters.length - 1);
      geometry.current = {
        start: section.getBoundingClientRect().top + window.scrollY - top,
        distance,
        width,
        pinned: canPin,
      };
      section.style.setProperty('--story-height', `${availableHeight}px`);
      section.style.setProperty('--story-distance', `${distance}px`);
      section.dataset.pinned = String(canPin);
      setPinned(canPin);
      if (!canPin) {
        strip.style.transform = '';
      } else {
        update();
      }
    };
    const observer = new ResizeObserver(measure);
    observer.observe(windowElement);
    panels.current.forEach((panel) => { if (panel) observer.observe(panel); });
    // 목록이나 폰트가 늦게 로드되어 위쪽 높이가 바뀌는 경우에도 시작점을 갱신한다.
    observer.observe(document.body);
    const visibility = new IntersectionObserver((entries) => {
      if (geometry.current.pinned) return;
      for (const entry of entries) {
        if (entry.isIntersecting) setActive(Number((entry.target as HTMLElement).dataset.chapter));
      }
    }, { rootMargin: '-20% 0px -50% 0px' });
    panels.current.forEach((panel) => { if (panel) visibility.observe(panel); });
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', measure);
    motion.addEventListener('change', measure);
    measure();
    return () => {
      observer.disconnect();
      visibility.disconnect();
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', measure);
      motion.removeEventListener('change', measure);
      cancelAnimationFrame(frame);
    };
  }, []);

  const goTo = (index: number) => {
    const { start, width, pinned: isPinned } = geometry.current;
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
    if (isPinned) window.scrollTo({ top: start + width * index, behavior });
    else document.getElementById(chapters[index].id)?.scrollIntoView({ behavior, block: 'start' });
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (!geometry.current.pinned || event.button !== 0 || (event.target as Element).closest('a,button')) return;
    drag.current = { id: event.pointerId, x: event.clientX, y: event.clientY, scroll: window.scrollY, horizontal: false };
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const origin = drag.current;
    if (!origin || origin.id !== event.pointerId) return;
    const dx = origin.x - event.clientX;
    const dy = origin.y - event.clientY;
    if (!origin.horizontal) {
      if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 8) { drag.current = null; return; }
      if (Math.abs(dx) < 8) return;
      origin.horizontal = true;
      event.currentTarget.setPointerCapture(event.pointerId);
    }
    const { start, distance } = geometry.current;
    window.scrollTo({ top: Math.max(start, Math.min(start + distance, origin.scroll + dx)), behavior: 'instant' });
  };
  const endDrag = (event: PointerEvent<HTMLDivElement>) => {
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };

  return (
    <div ref={root} className={styles.storyJourney} data-pinned={pinned}>
      <div className={styles.storyStage}>
        <aside className={styles.rail}>
          <nav className={styles.chapterNav} aria-label="스토리 목차">
            {chapters.map((chapter, index) => (
              <button key={chapter.id} onClick={() => goTo(index)} aria-current={active === index ? 'step' : undefined} aria-controls={chapter.id}>
                <span>{chapter.number}</span>{chapter.label}
              </button>
            ))}
          </nav>
          <p className={styles.scrollHint}>스크롤하여<br />이야기 읽기 <span aria-hidden="true">↓</span></p>
        </aside>
        <div ref={viewport} className={styles.storyViewport} onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={endDrag} onPointerCancel={endDrag} onLostPointerCapture={() => { drag.current = null; }}>
          <div ref={track} className={styles.storyTrack}>
            {Children.toArray(children).map((child, index) => (
              <div key={chapters[index].id} ref={(element) => { panels.current[index] = element; }} data-chapter={index} className={styles.storySlide} inert={pinned && active !== index ? true : undefined}>{child}</div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
