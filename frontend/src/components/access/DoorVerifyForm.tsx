import type { Space } from '../../types/api';
import Button from '../common/Button';
import Input from '../common/Input';
import Select from '../common/Select';

/** Mock Door 단말. 실제 스마트락 대신 이 화면이 출입 검증을 요청합니다. */
export interface DoorVerifyFormValue {
  accessKey?: string;
  spaceId?: string;
}

export interface DoorVerifyFormProps {
  spaces?: Space[];
  value: DoorVerifyFormValue;
  onChange: (next: DoorVerifyFormValue) => void;
  onSubmit: () => void;
  loading?: boolean;
}

export default function DoorVerifyForm({
  spaces = [],
  value,
  onChange,
  onSubmit,
  loading,
}: DoorVerifyFormProps) {
  const update = (patch: DoorVerifyFormValue) => onChange({ ...value, ...patch });

  return (
    <form
      className="panel"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <Select
        label="출입할 공간"
        required
        options={[
          { value: '', label: '공간을 선택해 주세요' },
          ...spaces.map((space) => ({ value: space.id, label: `${space.name} · ${space.location}` })),
        ]}
        value={value.spaceId ?? ''}
        onChange={(event) => update({ spaceId: event.target.value })}
      />
      <Input
        label="출입 키"
        required
        placeholder="발급받은 키를 입력해 주세요"
        autoComplete="off"
        help="예약한 공간과 다른 공간을 선택하면 거절되며, 그 시도도 기록됩니다."
        value={value.accessKey ?? ''}
        onChange={(event) => update({ accessKey: event.target.value.trim() })}
      />
      <Button
        type="submit"
        variant="primary"
        wide
        loading={loading}
        disabled={!value.accessKey || !value.spaceId}
      >
        출입 확인
      </Button>
    </form>
  );
}
