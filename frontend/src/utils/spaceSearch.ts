import type { SpaceListParams } from '../api/spaceApi';

export interface SpaceSearchFilters {
  keyword: string; location: string; minPrice: string; maxPrice: string;
  date: string; startTime: string; endTime: string;
}
export const EMPTY_SPACE_FILTERS: SpaceSearchFilters = {
  keyword: '', location: '', minPrice: '', maxPrice: '', date: '', startTime: '', endTime: '',
};
export const HALF_HOUR_TIMES = Array.from({ length: 48 }, (_, index) =>
  `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`);

export function validateSpaceSearch(value: SpaceSearchFilters): string | null {
  for (const price of [value.minPrice, value.maxPrice]) {
    if (price !== '' && (!/^\d+$/.test(price) || !Number.isSafeInteger(Number(price)))) return '요금은 0 이상의 정수로 입력해 주세요.';
  }
  if (value.minPrice !== '' && value.maxPrice !== '' && Number(value.minPrice) > Number(value.maxPrice)) return '최대 요금은 최소 요금 이상이어야 합니다.';
  const selected = [value.date, value.startTime, value.endTime].filter(Boolean).length;
  if (selected > 0 && selected < 3) return '시간대 검색은 이용 날짜, 시작 시간, 종료 시간을 모두 선택해 주세요.';
  if (selected === 3) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value.date) || !Number.isFinite(Date.parse(value.date)) || new Date(value.date).toISOString().slice(0, 10) !== value.date) return '올바른 이용 날짜를 선택해 주세요.';
    if (!HALF_HOUR_TIMES.includes(value.startTime) || !HALF_HOUR_TIMES.includes(value.endTime)) return '시간은 30분 단위로 선택해 주세요.';
    if (value.startTime >= value.endTime) return '종료 시간은 시작 시간보다 늦어야 합니다.';
  }
  return null;
}
export function toSpaceSearchParams(value: SpaceSearchFilters): SpaceListParams {
  const error = validateSpaceSearch(value);
  if (error) throw new Error(error);
  return {
    keyword: value.keyword.trim() || undefined, location: value.location || undefined,
    minPrice: value.minPrice === '' ? undefined : Number(value.minPrice),
    maxPrice: value.maxPrice === '' ? undefined : Number(value.maxPrice),
    ...(value.date && value.startTime && value.endTime ? {
      date: value.date, startTime: `${value.startTime}:00`, endTime: `${value.endTime}:00`,
    } : {}),
  };
}
