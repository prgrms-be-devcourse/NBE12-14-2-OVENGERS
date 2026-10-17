'use client';
import { useState } from 'react';
import { getLocationRanking, getSpaceRanking } from '../../api/adminStatsApi';
import type { LocationRanking, RankingMetric, RankingPeriod, SpaceRanking } from '../../api/adminStatsApi';
import { useAsync } from '../../hooks/useApi';
import {
  RANKING_METRIC_OPTIONS, RANKING_NOTES, RANKING_PERIOD_OPTIONS,
  canViewRankingDate, defaultRankingDate, rankChange, rankingPeriodLabel, rankingStatusNotice, shiftRankingDate,
} from '../../utils/adminRanking';
import { SPACE_REGIONS } from '../../utils/spaceRegion';
import Badge from '../common/Badge';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';
import LoadingSpinner from '../common/LoadingSpinner';
import Select from '../common/Select';
import styles from './SpaceRankingSection.module.css';

type View = 'SPACE' | 'LOCATION';
type Ranking = SpaceRanking | LocationRanking;

const won = (value: number) => `${value.toLocaleString('ko-KR')}원`;
const confirmedAt = (value: string | null) => (value ? `${value.replace('T', ' ').slice(0, 16)} 확정` : '');

/** 끝난 기간의 확정 랭킹(api-spec §14). 진행 중인 기간은 순위 대신 확정 예정 안내를 보여 준다. */
export default function SpaceRankingSection() {
  const [view, setView] = useState<View>('SPACE');
  const [period, setPeriod] = useState<RankingPeriod>('DAILY');
  const [date, setDate] = useState(() => defaultRankingDate('DAILY'));
  const [metric, setMetric] = useState<RankingMetric>('NET_REVENUE');
  const [location, setLocation] = useState('');

  const { data, loading, error, run } = useAsync<Ranking>(
    () => view === 'SPACE'
      ? getSpaceRanking({ period, date, metric, location: location || undefined, limit: 10 })
      : getLocationRanking({ period, date, metric, limit: 10 }),
    [view, period, date, metric, location],
  );

  const changePeriod = (next: RankingPeriod) => { setPeriod(next); setDate(defaultRankingDate(next)); };
  const canNext = canViewRankingDate(period, shiftRankingDate(period, date, 1));
  const notice = data ? rankingStatusNotice(data) : null;
  const label = data ? rankingPeriodLabel(data.period, data.periodStart, data.periodEnd) : date;

  return <section className={styles.ranking} aria-label="베스트 공간 랭킹">
    <div className={styles.heading}>
      <h2>베스트 공간 랭킹</h2>
      <p>끝난 기간의 확정값입니다. 매일 00:01에 방금 끝난 기간을 확정합니다.</p>
    </div>
    <div className={styles.filters}>
      <Select label="보기" value={view} onChange={e => setView(e.target.value as View)}
        options={[{ value: 'SPACE', label: '공간별' }, { value: 'LOCATION', label: '지역별' }]} />
      <Select label="기간" value={period} onChange={e => changePeriod(e.target.value as RankingPeriod)} options={RANKING_PERIOD_OPTIONS} />
      <Select label="정렬 기준" value={metric} onChange={e => setMetric(e.target.value as RankingMetric)} options={RANKING_METRIC_OPTIONS} />
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
    {!loading && data && !notice && <>
      <table className={styles.table}>
        <thead><tr>
          <th scope="col">순위</th><th scope="col">변동</th>
          <th scope="col">{view === 'SPACE' ? '오피스' : '지역'}</th>
          {view === 'LOCATION' && <th scope="col">오피스 수</th>}
          <th scope="col">순매출</th><th scope="col">예약 건수</th><th scope="col">예약 슬롯 수</th>
        </tr></thead>
        <tbody>
          {view === 'SPACE'
            ? (data as SpaceRanking).items.map(item => {
              const change = rankChange(item.rank, item.previousRank);
              return <tr key={item.spaceId}>
                <td>{item.rank}</td><td className={styles[change.kind]}>{change.label}</td>
                <td>{item.spaceName ?? `공간 #${item.spaceId}`} <small>{item.location}</small>{item.spaceStatus === 'INACTIVE' && <> <Badge>운영 중지</Badge></>}</td>
                <td>{won(item.netRevenue)}</td><td>{item.reservationCount.toLocaleString()}건</td><td>{item.slotCount.toLocaleString()}슬롯</td>
              </tr>;
            })
            : (data as LocationRanking).items.map(item => {
              const change = rankChange(item.rank, item.previousRank);
              return <tr key={item.location}>
                <td>{item.rank}</td><td className={styles[change.kind]}>{change.label}</td>
                <td>{item.location}</td><td>{item.spaceCount}곳</td>
                <td>{won(item.netRevenue)}</td><td>{item.reservationCount.toLocaleString()}건</td><td>{item.slotCount.toLocaleString()}슬롯</td>
              </tr>;
            })}
        </tbody>
      </table>
      <p className={styles.computed}>{confirmedAt(data.computedAt)}</p>
    </>}
    <ul className={styles.notes}>{RANKING_NOTES.map(note => <li key={note}>{note}</li>)}</ul>
  </section>;
}
