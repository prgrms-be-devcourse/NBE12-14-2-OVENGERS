import type { RankingMetric, RankingPeriod, RankingStatus } from '../api/adminStatsApi';
import type { SelectOption } from '../types/api';
import { seoulDate } from './adminDashboard';

export const RANKING_PERIOD_OPTIONS: SelectOption[] = [
  { value: 'DAILY', label: '일간' },
  { value: 'WEEKLY', label: '주간' },
  { value: 'MONTHLY', label: '월간' },
];
export const RANKING_METRIC_OPTIONS: SelectOption[] = [
  { value: 'NET_REVENUE', label: '순매출' },
  { value: 'RESERVATION_COUNT', label: '예약 건수' },
  { value: 'SLOT_COUNT', label: '예약 슬롯 수' },
];

/** 화면 유의사항. 문구는 docs/plans/space-ranking/00-space-ranking-plan.md §4 를 따른다. */
export const RANKING_NOTES = [
  '순위는 해당 기간이 끝난 뒤 24:00(다음날 00:00) 기준으로 확정된 값이며, 이후 환불 등 변경은 해당 기간의 값에 반영되지 않습니다.',
  '오늘·이번 주·이번 달은 집계 중입니다. 오늘 순위는 내일 00:00 기준으로 확정됩니다.',
  '순매출은 결제에서 환불을 뺀 값이며 위약금을 포함합니다. 환불·위약금은 발생한 날에 반영됩니다.',
  '지표 값이 같으면 순매출 순위는 예약 건수, 예약 건수 순위는 순매출, 슬롯 수 순위는 예약 건수, 그래도 같으면 공간 ID가 작은 순서로 정해집니다.',
  '취소된 예약은 예약 건수에 포함되지 않지만 위약금은 순매출에 포함됩니다.',
] as const;

// 날짜 문자열(YYYY-MM-DD)은 UTC 자정으로만 계산한다. 시간대 변환 없이 달력 연산만 하기 위해서다.
function toUtc(date: string): Date {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}
function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}
function addDays(date: string, days: number): string {
  const d = toUtc(date);
  d.setUTCDate(d.getUTCDate() + days);
  return toIso(d);
}

/** date가 속한 기간의 첫 날. 주는 월요일에 시작한다(서버와 같은 규칙). */
export function rankingPeriodStart(period: RankingPeriod, date: string): string {
  if (period === 'DAILY') return date;
  if (period === 'MONTHLY') return `${date.slice(0, 7)}-01`;
  const weekday = toUtc(date).getUTCDay(); // 0=일
  return addDays(date, -((weekday + 6) % 7));
}

/** 가장 최근에 "끝난" 기간에 속한 날짜. 진행 중인 기간은 순위가 없으므로 기본으로 보여 주지 않는다. */
export function defaultRankingDate(period: RankingPeriod, now = new Date()): string {
  const today = seoulDate(now);
  if (period === 'DAILY') return addDays(today, -1);
  if (period === 'WEEKLY') return addDays(today, -7);
  return addDays(rankingPeriodStart('MONTHLY', today), -1);
}

/** 이전(-1)·다음(+1) 기간으로 이동한 날짜. 월간은 항상 그 달의 1일을 돌려준다. */
export function shiftRankingDate(period: RankingPeriod, date: string, direction: 1 | -1): string {
  if (period === 'DAILY') return addDays(date, direction);
  if (period === 'WEEKLY') return addDays(date, 7 * direction);
  const d = toUtc(`${date.slice(0, 7)}-01`);
  d.setUTCMonth(d.getUTCMonth() + direction);
  return toIso(d);
}

/** 서버는 현재 기간보다 미래의 date를 400으로 거절하므로 화면에서 미리 막는다. */
export function canViewRankingDate(period: RankingPeriod, date: string, now = new Date()): boolean {
  return rankingPeriodStart(period, date) <= rankingPeriodStart(period, seoulDate(now));
}

export function rankingPeriodLabel(period: RankingPeriod, periodStart: string, periodEnd: string): string {
  if (period === 'DAILY') return periodStart;
  if (period === 'WEEKLY') return `${periodStart} ~ ${addDays(periodEnd, -1)}`;
  return `${Number(periodStart.slice(0, 4))}년 ${Number(periodStart.slice(5, 7))}월`;
}

export type RankChange = { kind: 'up' | 'down' | 'same' | 'none'; label: string };

/** 직전 기간 대비 변동. 직전 기간에 순위가 없으면(null) 비교하지 않는다. */
export function rankChange(rank: number, previousRank: number | null): RankChange {
  if (previousRank === null) return { kind: 'none', label: '–' };
  if (previousRank > rank) return { kind: 'up', label: `▲${previousRank - rank}` };
  if (previousRank < rank) return { kind: 'down', label: `▼${rank - previousRank}` };
  return { kind: 'same', label: '–' };
}

/** 순위 표 대신 보여 줄 안내. 표를 보여 줘도 되면 null. */
export function rankingStatusNotice(
  ranking: { status: RankingStatus; confirmAt?: string | null; items: unknown[] },
): string | null {
  if (ranking.status === 'PENDING') {
    const at = (ranking.confirmAt ?? '').replace('T', ' ').slice(0, 16);
    return `집계 중입니다. 이 기간의 순위는 ${at} 기준으로 확정됩니다.`;
  }
  if (ranking.status === 'UNAVAILABLE') return '이 기간은 확정된 기록이 없습니다.';
  if (ranking.items.length === 0) return '이 기간에는 집계된 실적이 없습니다.';
  return null;
}

export function metricLabel(metric: RankingMetric): string {
  return RANKING_METRIC_OPTIONS.find(option => option.value === metric)?.label ?? metric;
}
