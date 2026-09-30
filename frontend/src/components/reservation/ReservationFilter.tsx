import type { ReservationStatus, Space } from '../../types/api';
import { RESERVATION_STATUS_OPTIONS } from '../../constants/enums';
import Select from '../common/Select';
import Input from '../common/Input';

export interface ReservationFilterValue {
  date?: string;
  spaceId?: string;
  status?: ReservationStatus | '';
  keyword?: string;
}

export interface ReservationFilterProps {
  value: ReservationFilterValue;
  onChange: (next: ReservationFilterValue) => void;
  showDate?: boolean;
  showSpace?: boolean;
  spaces?: Space[];
  spacesLoading?: boolean;
  spacesError?: unknown;
  onRetrySpaces?: () => void;
}

export default function ReservationFilter({
  value,
  onChange,
  showDate = false,
  showSpace = false,
  spaces = [],
  spacesLoading = false,
  spacesError = null,
  onRetrySpaces,
}: ReservationFilterProps) {
  const update = (patch: ReservationFilterValue) => onChange({ ...value, ...patch });

  const spaceOptions = spacesLoading
    ? [{ value: '', label: '오피스 목록을 불러오는 중...' }]
    : [{ value: '', label: '전체 오피스' }, ...spaces.map((s) => ({ value: s.id, label: s.name }))];

  return (
    <div className="filters">
      {showDate && (
        <label>
          <span>이용 날짜</span>
          <input
            type="date"
            value={value.date ?? ''}
            onChange={(event) => update({ date: event.target.value })}
          />
        </label>
      )}
      {showSpace && (
        <div className="space-filter-wrap">
          <Select
            label="오피스"
            options={spaceOptions}
            value={value.spaceId ?? ''}
            onChange={(event) => update({ spaceId: event.target.value })}
            disabled={spacesLoading}
          />
          {Boolean(spacesError) && (
            <div className="space-error-hint" style={{ fontSize: '0.85rem', color: 'var(--red, #e53e3e)', marginTop: '4px' }}>
              <span>오피스 목록을 불러오지 못했습니다.</span>{' '}
              {onRetrySpaces && (
                <button
                  type="button"
                  onClick={onRetrySpaces}
                  style={{ textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0 }}
                >
                  다시 시도
                </button>
              )}
            </div>
          )}
        </div>
      )}
      <Select
        label="예약 상태"
        options={RESERVATION_STATUS_OPTIONS}
        value={value.status ?? ''}
        onChange={(event) => update({ status: event.target.value as ReservationStatus | '' })}
      />
      {!showDate && !showSpace && (
        <Input
          label="검색"
          placeholder="오피스 이름"
          value={value.keyword ?? ''}
          onChange={(event) => update({ keyword: event.target.value })}
        />
      )}
    </div>
  );
}
