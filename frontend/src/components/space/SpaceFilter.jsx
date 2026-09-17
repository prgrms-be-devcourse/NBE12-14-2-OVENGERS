import { SPACE_STATUS_OPTIONS } from '../../constants/enums';
import Input from '../common/Input';
import Select from '../common/Select';

export default function SpaceFilter({ value, onChange, showStatus = false }) {
  const update = (patch) => onChange({ ...value, ...patch });

  return (
    <div className="filters">
      <label>
        <span>날짜</span>
        <input
          type="date"
          value={value.date ?? ''}
          onChange={(event) => update({ date: event.target.value })}
        />
      </label>
      <Input
        label="검색"
        placeholder="공간 이름 또는 위치"
        value={value.keyword ?? ''}
        onChange={(event) => update({ keyword: event.target.value })}
      />
      {showStatus && (
        <Select
          label="상태"
          options={SPACE_STATUS_OPTIONS}
          value={value.status ?? ''}
          onChange={(event) => update({ status: event.target.value })}
        />
      )}
    </div>
  );
}
