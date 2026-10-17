import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';
import type { ReservationStatus } from '../types/api';

export type RankingPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type RankingMetric = 'NET_REVENUE' | 'RESERVATION_COUNT' | 'SLOT_COUNT';
export type RankingStatus = 'CONFIRMED' | 'PENDING' | 'UNAVAILABLE';

export type StatusCounts = Record<ReservationStatus, number>;
export interface StatusCountsResult { date: string; counts: StatusCounts }

interface RankingBase {
  period: RankingPeriod;
  /** 포함 */
  periodStart: string;
  /** 제외(다음 기간 시작) */
  periodEnd: string;
  metric: RankingMetric;
  status: RankingStatus;
  /** PENDING일 때만 값이 있다 */
  confirmAt: string | null;
  /** 확정 행이 있을 때만 값이 있다 */
  computedAt: string | null;
}
export interface SpaceRankingItem {
  rank: number; previousRank: number | null;
  spaceId: number; spaceName: string | null; location: string; spaceStatus: 'ACTIVE' | 'INACTIVE' | null;
  netRevenue: number; reservationCount: number; slotCount: number;
}
export interface LocationRankingItem {
  rank: number; previousRank: number | null; location: string; spaceCount: number;
  netRevenue: number; reservationCount: number; slotCount: number;
}
export interface SpaceRanking extends RankingBase { items: SpaceRankingItem[] }
export interface LocationRanking extends RankingBase { items: LocationRankingItem[] }

export interface RankingQuery { period: RankingPeriod; date: string; metric?: RankingMetric; limit?: number }
export interface SpaceRankingQuery extends RankingQuery { location?: string }

/** 날짜 생략 시 서버가 오늘(KST)로 본다. spaceId는 특정 공간만 센다. */
export function getStatusCounts({ date, spaceId }: { date?: string; spaceId?: string | number } = {}) {
  return api.get<StatusCountsResult>(API_ROUTES.admin.statusCounts, { query: { date, spaceId } });
}

export function getSpaceRanking(query: SpaceRankingQuery) {
  return api.get<SpaceRanking>(API_ROUTES.admin.spaceRanking, { query: { ...query } });
}

export function getLocationRanking(query: RankingQuery) {
  return api.get<LocationRanking>(API_ROUTES.admin.locationRanking, { query: { ...query } });
}
