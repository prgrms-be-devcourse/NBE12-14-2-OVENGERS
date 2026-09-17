import { RESERVATION_STATUS_OPTIONS } from '../../constants/enums';
import Select from '../common/Select';
import Input from '../common/Input';

export default function ReservationFilter({ value, onChange, showDate = false, showSpace = false, spaces = [] }) {
  const update = (patch) => onChange({ ...value, ...patch });

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
        <Select
          label="공간"
          options={[{ value: '', label: '전체 공간' }, ...spaces.map((s) => ({ value: s.id, label: s.name }))]}
          value={value.spaceId ?? ''}
          onChange={(event) => update({ spaceId: event.target.value })}
        />
      )}
      <Select
        label="예약 상태"
        options={RESERVATION_STATUS_OPTIONS}
        value={value.status ?? ''}
        onChange={(event) => update({ status: event.target.value })}
      />
      {!showDate && !showSpace && (
        <Input
          label="검색"
          placeholder="공간 이름"
          value={value.keyword ?? ''}
          onChange={(event) => update({ keyword: event.target.value })}
        />
      )}
    </div>
  );
}
