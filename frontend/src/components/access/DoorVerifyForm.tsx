import type { ReservationSummary } from '../../types/api';
import Button from '../common/Button';
import Input from '../common/Input';
import Select from '../common/Select';

/** Mock Door 단말. 실제 스마트락 대신 이 화면이 출입 검증을 요청합니다. */
export interface DoorVerifyFormValue {
  accessKey?: string;
  reservationId?: string;
}

export interface DoorVerifyFormProps {
  reservations?: ReservationSummary[];
  disabled?: boolean;
  value: DoorVerifyFormValue;
  onChange: (next: DoorVerifyFormValue) => void;
  onSubmit: () => void;
  loading?: boolean;
}

export default function DoorVerifyForm({
  reservations = [],
  disabled = false,
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
        if (!disabled && !loading && value.reservationId && value.accessKey?.trim()) onSubmit();
      }}
    >
      <Select
        label="내 예약"
        required
        disabled={disabled || loading}
        options={[
          { value: '', label: '출입할 예약을 선택해 주세요' },
          ...reservations.map((reservation) => ({
            value: reservation.reservationId,
            label: `${reservation.spaceName} · ${reservation.date || reservation.startTime.slice(0, 10)} ${reservation.startTime.includes('T') ? reservation.startTime.slice(11, 16) : reservation.startTime.slice(0, 5)}–${reservation.endTime.includes('T') ? reservation.endTime.slice(11, 16) : reservation.endTime.slice(0, 5)} · SK-${reservation.reservationId}`,
          })),
        ]}
        value={value.reservationId ?? ''}
        onChange={(event) => update({ reservationId: event.target.value, accessKey: '' })}
      />
      <Input
        label="출입 키"
        required
        placeholder="복사한 출입 키를 붙여넣어 주세요"
        autoComplete="off"
        help="예약 상세에서 복사한 출입 키를 붙여넣어 주세요."
        disabled={disabled || loading || !value.reservationId}
        spellCheck={false}
        value={value.accessKey ?? ''}
        onChange={(event) => update({ accessKey: event.target.value.replace(/\s/g, '') })}
      />
      <Button
        type="submit"
        variant="primary"
        wide
        loading={loading}
        disabled={disabled || !value.accessKey?.trim() || !value.reservationId}
      >
        출입 확인
      </Button>
    </form>
  );
}
