import type { SelectOption } from '../types/api';

/** 서버가 받는 지역 값(SpaceRegion enum). 지역을 늘리면 서버와 함께 바꾼다. */
export const SPACE_REGIONS = ['판교', '강남', '하남'] as const;

export function isSpaceRegion(value: string): boolean {
  return (SPACE_REGIONS as readonly string[]).includes(value);
}

/**
 * 폼 선택지. 기존 공간의 값이 세 지역이 아니면(주소 문자열 등) 그 값을 맨 앞에 남겨
 * 무엇이 현재 저장돼 있는지 보이게 하고, 저장하려면 세 지역 중 하나로 바꾸게 한다.
 */
export function regionOptions(current: string): SelectOption[] {
  const base: SelectOption[] = SPACE_REGIONS.map(region => ({ value: region, label: region }));
  if (!current) return [{ value: '', label: '지역을 선택해 주세요' }, ...base];
  if (isSpaceRegion(current)) return [{ value: '', label: '지역을 선택해 주세요' }, ...base];
  return [{ value: current, label: `${current} (판교·강남·하남 중에서 변경 필요)` }, ...base];
}
