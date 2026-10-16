'use client';

import { useState } from 'react';
import Link from 'next/link';
import { getPublicLocationRanking, getPublicSpaceRanking } from '../../api/statsApi';
import type { PublicLocationRanking, PublicRankingMetric, PublicSpaceRanking } from '../../api/statsApi';
import type { RankingPeriod } from '../../api/adminStatsApi';
import { useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import {
  PUBLIC_RANKING_METRIC_OPTIONS, PUBLIC_RANKING_NOTES, RANKING_PERIOD_OPTIONS,
  canViewRankingDate, defaultRankingDate, rankChange, rankingPeriodLabel, rankingStatusNotice, shiftRankingDate,
} from '../../utils/adminRanking';
import { SPACE_REGIONS } from '../../utils/spaceRegion';
import Button from '../../components/common/Button';
import ErrorMessage from '../../components/common/ErrorMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import Select from '../../components/common/Select';
import styles from '../../components/admin/SpaceRankingSection.module.css';

type View = 'SPACE' | 'LOCATION';
type Ranking = PublicSpaceRanking | PublicLocationRanking;

/** 회원용 베스트 랭킹(api-spec §14-2). 관리자 랭킹과 같은 확정값이며 순매출은 보여 주지 않는다. */
export default function RankingPage() {
  const [view, setView] = useState<View>('SPACE');
  const [period, setPeriod] = useState<RankingPeriod>('DAILY');
  const [date, setDate] = useState(() => defaultRankingDate('DAILY'));
  const [metric, setMetric] = useState<PublicRankingMetric>('RESERVATION_COUNT');
  const [location, setLocation] = useState('');

  const { data, loading, error, run } = useAsync<Ranking>(
    () => view === 'SPACE'
      ? getPublicSpaceRanking({ period, date, metric, location: location || undefined, limit: 10 })
      : getPublicLocationRanking({ period, date, metric, limit: 10 }),
    [view, period, date, metric, location],
  );

  const changePeriod = (next: RankingPeriod) => { setPeriod(next); setDate(defaultRankingDate(next)); };
  const canNext = canViewRankingDate(period, shiftRankingDate(period, date, 1));
  const notice = data ? rankingStatusNotice(data) : null;
  const label = data ? rankingPeriodLabel(data.period, data.periodStart, data.periodEnd) : date;

  return <section className={styles.ranking} style={{ marginTop: 0 }} aria-label="베스트 공간 랭킹">
    <div className={styles.heading}>
      <h1>베스트 랭킹</h1>
      <p>어떤 오피스가 가장 많이 예약됐는지 확인해 보세요. 끝난 기간의 확정값입니다.</p>
    </div>
    <div className={styles.filters}>
      <Select label="보기" value={view} onChange={e => setView(e.target.value as View)}
        options={[{ value: 'SPACE', label: '오피스별' }, { value: 'LOCATION', label: '지역별' }]} />
      <Select label="기간" value={period} onChange={e => changePeriod(e.target.value as RankingPeriod)} options={RANKING_PERIOD_OPTIONS} />
      <Select label="정렬 기준" value={metric} onChange={e => setMetric(e.target.value as PublicRankingMetric)} options={PUBLIC_RANKING_METRIC_OPTIONS} />
      {view === 'SPACE' && <Select label="지역" value={location} onChange={e => setLocation(e.target.value)}
        options={[{ value: '', label: '전체 지역' }, ...SPACE_REGIONS.map(region => ({ value: region, label: region }))]} />}
    </div>
    <div className={styles.nav}>
      <Button size="small" onClick={() => setDate(shiftRankingDate(period, date, -1))}>← 이전</Button>
      <strong>{label}</strong>
      <Button size="small" disabled={!canNext} onClick={() => setDate(shiftRankingDate(period, date, 1))}>다음 →</Button>
    </div>
    <ErrorMessage error={error} onRetry={() => { void run().catch(() => {}); }} />
    {loading && <LoadingSpinner label="랭킹을 불러오고 있습니다…" />}
    {!loading && data && notice && <p className={styles.notice} role="status">{notice}</p>}
    {!loading && data && !notice && <table className={styles.table}>
      <thead><tr>
        <th scope="col">순위</th><th scope="col">변동</th>
        <th scope="col">{view === 'SPACE' ? '오피스' : '지역'}</th>
        {view === 'LOCATION' && <th scope="col">오피스 수</th>}
        <th scope="col">예약 건수</th><th scope="col">예약 슬롯 수</th>
      </tr></thead>
      <tbody>
        {view === 'SPACE'
          ? (data as PublicSpaceRanking).items.map(item => {
            const change = rankChange(item.rank, item.previousRank);
            return <tr key={item.spaceId}>
              <td>{item.rank}</td><td className={styles[change.kind]}>{change.label}</td>
              <td>{item.spaceStatus === 'INACTIVE'
                ? <>{item.spaceName ?? `오피스 #${item.spaceId}`} <small>{item.location} · 운영 중지</small></>
                : <Link href={ROUTES.spaceDetail(item.spaceId)}>{item.spaceName ?? `오피스 #${item.spaceId}`}</Link>}
                {item.spaceStatus !== 'INACTIVE' && <small>{item.location}</small>}</td>
              <td>{item.reservationCount.toLocaleString()}건</td><td>{item.slotCount.toLocaleString()}슬롯</td>
            </tr>;
          })
          : (data as PublicLocationRanking).items.map(item => {
            const change = rankChange(item.rank, item.previousRank);
            return <tr key={item.location}>
              <td>{item.rank}</td><td className={styles[change.kind]}>{change.label}</td>
              <td>{item.location}</td><td>{item.spaceCount}곳</td>
              <td>{item.reservationCount.toLocaleString()}건</td><td>{item.slotCount.toLocaleString()}슬롯</td>
            </tr>;
          })}
      </tbody>
    </table>}
    <ul className={styles.notes}>{PUBLIC_RANKING_NOTES.map(note => <li key={note}>{note}</li>)}</ul>
  </section>;
}
