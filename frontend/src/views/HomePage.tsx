'use client';

import { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { getSpaces } from '@/api/spaceApi';
import { useAsync } from '@/hooks/useApi';
import { ROUTES } from '@/constants/routePaths';
import styles from './HomePage.module.css';
import StoryScroller from './StoryScroller';
import HomeReservations from '../components/reservation/HomeReservations';
import AdminDashboard from '../components/admin/AdminDashboard';

const fetchFeaturedSpaces = () => getSpaces({ page: 0, size: 3, status: 'ACTIVE' });

function Arrow({ down = false }: { down?: boolean }) {
  return <span aria-hidden="true">{down ? '↓' : '→'}</span>;
}

function OfficePhoto({ src, name }: { src: string | null; name: string }) {
  const [failed, setFailed] = useState(false);
  return src && !failed ? (
    <img src={src} alt={name} loading="lazy" decoding="async" onError={() => setFailed(true)} />
  ) : (
    <div className={styles.photoPlaceholder}><span>Slot Key</span><small>공간 사진 준비 중</small></div>
  );
}

export default function HomePage() {
  const { data, loading, error, run } = useAsync(fetchFeaturedSpaces, []);

  return (
    <div className={styles.home}>
      <section data-home-hero className={styles.hero} aria-labelledby="home-title">
        <Image className={styles.heroPhoto} src="/images/main/pangyo-blue-hour.webp" alt="푸른 저녁 하늘 아래 곡선형 유리 외벽과 따뜻한 조명이 어우러진 건축 이미지" fill priority sizes="100vw" />
        <div className={styles.heroShade} />
        <div className={styles.heroContent}>
          <h1 id="home-title"><span>몰입할 공간</span><strong>필요한 만큼</strong></h1>
          <a className={`${styles.outlineButton} ${styles.heroButton}`} href="#offices">공간 둘러보기 <Arrow down /></a>
        </div>
        <a href="#offices" className={styles.scrollCue} aria-label="아래 공간 목록으로 이동"><Arrow down /></a>
      </section>

      <HomeReservations />
      <AdminDashboard compact />

      <section id="offices" className={styles.offices} aria-labelledby="offices-title">
        <div className={styles.sectionHeading}>
          <h2 id="offices-title">오늘, 어디에서 일할까요?</h2>
          <Link href={ROUTES.spaces} className={styles.textLink}>전체 공간 보기 <Arrow /></Link>
        </div>
        {loading && <div className={styles.officeGrid} role="status" aria-label="공간을 불러오는 중">{[0, 1, 2].map((n) => <div key={n} className={styles.skeleton} />)}</div>}
        {!loading && error && <div className={styles.emptyState} role="alert"><p>공간 정보를 불러오지 못했습니다</p><button className={styles.outlineButton} onClick={() => { void run().catch(() => {}); }}>다시 불러오기 <Arrow /></button></div>}
        {!loading && !error && !data?.content.length && <div className={styles.emptyState}><p>새로운 공간을 준비하고 있습니다</p><Link className={styles.textLink} href={ROUTES.spaces}>전체 공간 확인하기 <Arrow /></Link></div>}
        {!loading && !error && !!data?.content.length && <div className={styles.officeGrid}>
          {data.content.map((space) => <Link key={space.id} href={ROUTES.spaceDetail(space.id)} className={styles.officeCard}>
            <div className={styles.cardPhoto}><OfficePhoto src={space.imagePath} name={space.name} /></div>
            <div className={styles.cardBody}><div><h3>{space.name}</h3><p>{space.location} · 최대 {space.capacity}인</p></div><Arrow /></div>
          </Link>)}
        </div>}
      </section>

      <section id="story" className={styles.storyIntro} aria-labelledby="story-title">
        <div><p className={styles.eyebrow}>OUR STORY</p><h2 id="story-title">일에 집중하기까지<br />필요한 일을 줄입니다</h2></div>
        <p className={styles.storyCopy}>공간을 찾고 시간을 정하는 과정이<br />일의 흐름을 끊지 않도록<br /><br />준비에 쓰는 시간은 줄이고<br />하고 싶은 일에 더 가까워지도록</p>
      </section>

      <StoryScroller>
          <section id="story-space" className={styles.chapter} aria-labelledby="space-story-title">
            <div><p className={styles.eyebrow}>01 &nbsp; SPACE</p><h2 id="space-story-title">좋은 공간은<br />해야 할 일에 집중하게 합니다</h2><p className={styles.storyCopy}>혼자 깊이 생각하는 시간과<br />함께 의견을 나누는 시간<br /><br />오늘 할 일과 함께할 사람을 기준으로<br />나에게 맞는 공간을 고릅니다</p></div>
            <figure className={styles.storyPhoto}><Image src="/images/main/workspace-story.webp" alt="자연광이 들어오는 회의 공간과 창가 업무 자리" width={1536} height={864} sizes="(max-width: 800px) 90vw, 45vw" /><figcaption>대화에 집중하는 공간, 생각을 이어가는 자리 <span>공간 이미지 예시</span></figcaption></figure>
          </section>
          <section id="story-time" className={`${styles.chapter} ${styles.chapterWhite}`} aria-labelledby="time-story-title">
            <div><p className={styles.eyebrow}>02 &nbsp; TIME</p><h2 id="time-story-title">공간이 필요한 시간은<br />저마다 다르니까</h2><p className={styles.storyCopy}>짧게 끝낼 회의도<br />조금 더 이어가고 싶은 작업도 있습니다<br /><br />정해진 하루에 일을 맞추기보다<br />오늘의 일정에 공간을 맞출 수 있도록</p></div>
            <figure className={styles.timelinePanel} aria-label="예약 시간 선택 예시: 오후 2시부터 3시까지 선택">
              <h3>내 일정에 맞춘 시간</h3><div className={styles.timeline}><div className={styles.selectedTime}><span>선택한 시간</span></div>{['14:00', '14:30', '15:00', '15:30', '16:00'].map((time) => <div key={time} className={styles.tick}><i /><span>{time}</span></div>)}</div><figcaption>화면 예시</figcaption>
            </figure>
          </section>
          <section id="story-connection" className={styles.chapter} aria-labelledby="connection-story-title">
            <div><p className={styles.eyebrow}>03 &nbsp; CONNECTION</p><h2 id="connection-story-title">예약에서 이용까지<br />하나의 흐름으로</h2><p className={styles.storyCopy}>Slot은 내가 선택한 시간<br />Key는 그 시간과 공간을 잇는 연결<br /><br />Slot Key라는 이름에 담은 생각입니다</p></div>
            <figure className={styles.connectionPanel}><div className={styles.connectionCards}><div className={styles.miniCard}><span>예약 정보</span><strong>나의 업무 공간</strong><small>14:00 – 15:00</small></div><div className={styles.connector} aria-hidden="true" /><div className={styles.miniCard}><span>출입 상태</span><svg className={styles.lock} viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><rect x="7" y="14" width="18" height="15" rx="3" /><path d="M11 14V9a5 5 0 0 1 10 0v5M16 20v4" /></svg><strong>모의 출입</strong></div></div><figcaption>현재 출입 기능은 모의 환경에서 제공됩니다</figcaption></figure>
          </section>
      </StoryScroller>
      <section className={styles.closing}><h2>이제 당신의 일을 시작할 공간</h2><Link href={ROUTES.spaces} className={styles.outlineButton}>공간 둘러보기 <Arrow /></Link></section>
    </div>
  );
}
