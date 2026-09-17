import { useState } from 'react';
import Input from '../common/Input';
import Select from '../common/Select';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';
import OperatingHoursField from './OperatingHoursField';
import { SPACE_STATUS, SPACE_STATUS_OPTIONS } from '../../constants/enums';

const EMPTY = {
  name: '',
  location: '',
  description: '',
  capacity: 4,
  pricePerSlot: 5000,
  imagePath: '',
  openingTime: '09:00',
  closingTime: '22:00',
  status: SPACE_STATUS.ACTIVE,
};

/** 등록·수정 공용 폼. mode 로 버튼 문구와 상태 필드 노출만 달라집니다. */
export default function SpaceForm({ mode = 'create', initialValue, submitting, error, onSubmit, onCancel }) {
  const [form, setForm] = useState({ ...EMPTY, ...initialValue });
  const [validation, setValidation] = useState({});

  const update = (patch) => setForm((prev) => ({ ...prev, ...patch }));

  const validate = () => {
    const next = {};
    if (!form.name.trim()) next.name = '공간 이름을 입력해 주세요.';
    if (!form.location.trim()) next.location = '위치를 입력해 주세요.';
    if (Number(form.capacity) <= 0) next.capacity = '수용 인원은 1명 이상이어야 합니다.';
    if (Number(form.pricePerSlot) <= 0 || Number(form.pricePerSlot) % 100 !== 0) {
      next.pricePerSlot = '요금은 100원 단위의 양수여야 합니다.';
    }
    if (form.openingTime >= form.closingTime) {
      next.hours = '운영 종료는 운영 시작보다 늦어야 합니다.';
    }
    setValidation(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!validate()) return;
    onSubmit({
      ...form,
      capacity: Number(form.capacity),
      pricePerSlot: Number(form.pricePerSlot),
    });
  };

  return (
    <form className="panel" onSubmit={handleSubmit} noValidate>
      <div className="form-grid">
        <div className="full">
          <Input
            label="공간 이름"
            required
            value={form.name}
            error={validation.name}
            onChange={(event) => update({ name: event.target.value })}
          />
        </div>
        <div className="full">
          <Input
            label="위치"
            required
            value={form.location}
            error={validation.location}
            onChange={(event) => update({ location: event.target.value })}
          />
        </div>

        <Input
          label="수용 인원"
          type="number"
          min="1"
          required
          value={form.capacity}
          error={validation.capacity}
          onChange={(event) => update({ capacity: event.target.value })}
        />
        <Input
          label="30분당 요금 (원)"
          type="number"
          min="100"
          step="100"
          required
          help="확정된 예약의 금액은 이후 요금을 바꿔도 유지됩니다."
          value={form.pricePerSlot}
          error={validation.pricePerSlot}
          onChange={(event) => update({ pricePerSlot: event.target.value })}
        />

        <OperatingHoursField
          openingTime={form.openingTime}
          closingTime={form.closingTime}
          onChange={update}
          error={validation.hours}
        />

        <div className="full">
          <Input
            label="이미지 주소"
            value={form.imagePath ?? ''}
            onChange={(event) => update({ imagePath: event.target.value })}
          />
        </div>
        <div className="full">
          <Input
            label="설명"
            value={form.description ?? ''}
            onChange={(event) => update({ description: event.target.value })}
          />
        </div>

        {mode === 'edit' && (
          <Select
            label="운영 상태"
            options={SPACE_STATUS_OPTIONS.filter((option) => option.value)}
            value={form.status}
            onChange={(event) => update({ status: event.target.value })}
          />
        )}
      </div>

      {mode === 'edit' && (
        <p className="note">
          운영 중지는 신규 예약에만 적용됩니다. 이미 확정된 예약은 그대로 유지됩니다.
        </p>
      )}

      <ErrorMessage error={error} />

      <div className="actions" style={{ marginTop: 24 }}>
        <Button type="submit" variant="primary" loading={submitting}>
          {mode === 'create' ? '공간 등록' : '수정 저장'}
        </Button>
        <Button onClick={onCancel}>취소</Button>
      </div>
    </form>
  );
}
