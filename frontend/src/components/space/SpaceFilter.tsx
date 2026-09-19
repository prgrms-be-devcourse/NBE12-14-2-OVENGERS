import type { SpaceStatus } from '../../types/api';
import { SPACE_STATUS_OPTIONS } from '../../constants/enums';
import Input from '../common/Input';
import Select from '../common/Select';

export interface SpaceFilterValue {
  date?: string;
  keyword?: string;
  status?: SpaceStatus | '';
}

export interface SpaceFilterProps {
  value: SpaceFilterValue;
  onChange: (next: SpaceFilterValue) => void;
  showStatus?: boolean;
}

export default function SpaceFilter({ value, onChange, showStatus = false }: SpaceFilterProps) {
  const update = (patch: SpaceFilterValue) => onChange({ ...value, ...patch });

  return (
    <div className="filters">
      {!showStatus && <label>
        <span>이용 날짜</span>
        <input
          type="date"
          value={value.date ?? ''}
          onChange={(event) => update({ date: event.target.value })}
        />
      </label>}
      <Input
        label="공간 검색"
        placeholder="공간 이름 또는 위치"
        value={value.keyword ?? ''}
        onChange={(event) => update({ keyword: event.target.value })}
      />
      {showStatus && (
        <Select
          label="상태"
          options={SPACE_STATUS_OPTIONS}
          value={value.status ?? ''}
          onChange={(event) => update({ status: event.target.value as SpaceStatus | '' })}
        />
      )}
    </div>
  );
}
