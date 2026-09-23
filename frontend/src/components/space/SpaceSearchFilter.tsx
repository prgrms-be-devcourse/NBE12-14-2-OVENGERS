'use client';
import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import Input from '../common/Input';
import Select from '../common/Select';
import Button from '../common/Button';
import { EMPTY_SPACE_FILTERS, HALF_HOUR_TIMES, validateSpaceSearch } from '../../utils/spaceSearch';
import type { SpaceSearchFilters } from '../../utils/spaceSearch';
import styles from './SpaceSearchFilter.module.css';

export default function SpaceSearchFilter({ onApply }: { onApply: (filters: SpaceSearchFilters) => void }) {
  const panelId = useId();
  const [expanded, setExpanded] = useState(false);
  const [draft, setDraft] = useState<SpaceSearchFilters>(EMPTY_SPACE_FILTERS);
  const [error, setError] = useState<string | null>(null);
  const update = (patch: Partial<SpaceSearchFilters>) => { setDraft((previous) => ({ ...previous, ...patch })); setError(null); };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const validation = validateSpaceSearch(draft);
    setError(validation);
    if (validation) setExpanded(true);
    if (!validation) onApply({ ...draft });
  };
  return <form noValidate className={styles.filter} onSubmit={submit} aria-label="공간 검색 조건">
    <div className={styles.primary}>
      <Input label="공간 검색" placeholder="공간 이름 또는 위치" value={draft.keyword} onChange={(event) => update({ keyword: event.target.value })} />
      <Select label="지역" value={draft.location} options={['', '판교', '하남', '강남'].map((value) => ({ value, label: value || '전체 지역' }))}
        onChange={(event) => update({ location: event.target.value })} />
    </div>
    <button type="button" className={styles.toggle} aria-expanded={expanded} aria-controls={panelId} onClick={() => setExpanded(!expanded)}>
      상세 검색 <span>{expanded ? '접기' : '펼치기'}</span>
      {[draft.minPrice, draft.maxPrice, draft.date, draft.startTime, draft.endTime].some(Boolean) && <small>조건 입력됨</small>}
    </button>
    <div id={panelId} hidden={!expanded}>
    <div className={styles.secondary}>
      <fieldset><legend>30분당 요금</legend><div className={styles.price}>
        <Input label="최소 요금 (원)" type="number" min={0} step={1} placeholder="제한 없음" value={draft.minPrice} onChange={(event) => update({ minPrice: event.target.value })} />
        <Input label="최대 요금 (원)" type="number" min={0} step={1} placeholder="제한 없음" value={draft.maxPrice} onChange={(event) => update({ maxPrice: event.target.value })} />
      </div></fieldset>
      <fieldset><legend>이용 시간대</legend><div className={styles.time}>
        <Input label="이용 날짜" type="date" value={draft.date} onChange={(event) => update({ date: event.target.value })} />
        <Select label="시작 시간" value={draft.startTime} options={[{ value: '', label: '선택 안 함' }, ...HALF_HOUR_TIMES.slice(0, -1).map((value) => ({ value, label: value }))]}
          onChange={(event) => update({ startTime: event.target.value, endTime: draft.endTime && draft.endTime <= event.target.value ? '' : draft.endTime })} />
        <Select label="종료 시간" value={draft.endTime} options={[{ value: '', label: '선택 안 함' }, ...HALF_HOUR_TIMES.filter((time) => !draft.startTime || time > draft.startTime).map((value) => ({ value, label: value }))]}
          onChange={(event) => update({ endTime: event.target.value })} />
      </div></fieldset>
    </div>
    <p className={styles.hint}>시간대 검색은 날짜와 시작·종료 시간을 모두 선택해 주세요.<br />조회 이후 다른 예약이 생길 수 있으며, 실제 예약 시 가능 여부를 다시 확인합니다.</p>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className={styles.footer}>
      <p>조건을 선택한 뒤 검색을 눌러 주세요.</p>
      <div className={styles.actions}><Button onClick={() => { setDraft(EMPTY_SPACE_FILTERS); setExpanded(false); setError(null); onApply(EMPTY_SPACE_FILTERS); }}>초기화</Button><Button type="submit" variant="primary">검색</Button></div>
    </div>
  </form>;
}
