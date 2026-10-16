import api from './client';
import { API_ROUTES } from '../constants/apiRoutes';
import type { LocationRanking, LocationRankingItem, SpaceRanking, SpaceRankingItem } from './adminStatsApi';
import type { RankingMetric, RankingPeriod } from './adminStatsApi';

/** 회원용 정렬 기준. 순매출(NET_REVENUE)은 서버가 400으로 거절한다. */
export type PublicRankingMetric = Exclude<RankingMetric, 'NET_REVENUE'>;

export type PublicSpaceRankingItem = Omit<SpaceRankingItem, 'netRevenue'>;
export type PublicLocationRankingItem = Omit<LocationRankingItem, 'netRevenue'>;
export type PublicSpaceRanking = Omit<SpaceRanking, 'items'> & { items: PublicSpaceRankingItem[] };
export type PublicLocationRanking = Omit<LocationRanking, 'items'> & { items: PublicLocationRankingItem[] };

export interface PublicRankingQuery { period: RankingPeriod; date: string; metric?: PublicRankingMetric; limit?: number }
export interface PublicSpaceRankingQuery extends PublicRankingQuery { location?: string }

export function getPublicSpaceRanking(query: PublicSpaceRankingQuery) {
  return api.get<PublicSpaceRanking>(API_ROUTES.stats.spaceRanking, { query: { ...query } });
}

export function getPublicLocationRanking(query: PublicRankingQuery) {
  return api.get<PublicLocationRanking>(API_ROUTES.stats.locationRanking, { query: { ...query } });
}
