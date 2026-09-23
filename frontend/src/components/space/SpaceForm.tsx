import { useState, useEffect, useRef } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import type { SpaceFormValues, SpaceStatus } from '../../types/api';
import type { SpacePayload } from '../../api/adminSpaceApi';
import type { MessageSource } from '../../constants/errorCodes';
import Input from '../common/Input';
import Select from '../common/Select';
import Button from '../common/Button';
import ErrorMessage from '../common/ErrorMessage';
import OperatingHoursField from './OperatingHoursField';
import SpacePhoto from './SpacePhoto';
import { validateImageFile } from '../../utils/imageValidation';
import { SPACE_STATUS, SPACE_STATUS_OPTIONS } from '../../constants/enums';

const EMPTY: SpaceFormValues = {
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

type ValidationKey = 'name' | 'location' | 'capacity' | 'pricePerSlot' | 'hours';

export interface SpaceFormProps {
  mode?: 'create' | 'edit';
  initialValue?: Partial<SpaceFormValues>;
  submitting?: boolean;
  error: MessageSource | null;
  onSubmit: (payload: SpacePayload, file?: File | null) => void;
  onCancel: () => void;
}

/** 등록·수정 공용 폼. mode 로 버튼 문구와 상태 필드 노출만 달라집니다. */
export default function SpaceForm({
  mode = 'create',
  initialValue,
  submitting,
  error,
  onSubmit,
  onCancel,
}: SpaceFormProps) {
  const [form, setForm] = useState<SpaceFormValues>({ ...EMPTY, ...initialValue });
  const [validation, setValidation] = useState<Partial<Record<ValidationKey, string>>>({});
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const validationError = validateImageFile(file);
    if (validationError) {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        setPreviewUrl(null);
      }
      setSelectedFile(null);
      setFileError(validationError);
      event.target.value = '';
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    setFileError(null);
    setSelectedFile(file);
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleClearFile = () => {
    setSelectedFile(null);
    setFileError(null);
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      setPreviewUrl(null);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const update = (patch: Partial<SpaceFormValues>) =>
    setForm((prev) => ({ ...prev, ...patch }));

  const validate = () => {
    const next: Partial<Record<ValidationKey, string>> = {};
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

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (fileError) return;
    if (!validate()) return;
    const payload: SpacePayload = {
      name: form.name.trim(),
      location: form.location.trim(),
      description: form.description ? form.description.trim() : null,
      capacity: Number(form.capacity),
      pricePerSlot: Number(form.pricePerSlot),
      openingTime: form.openingTime,
      closingTime: form.closingTime,
      status: form.status,
    };
    onSubmit(payload, selectedFile);
  };

  return (
    <form className="panel admin-space-form" onSubmit={handleSubmit} noValidate>
      <div className="form-grid">
        <h2 className="form-section-title">공간 기본 정보</h2>
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

        <h2 className="form-section-title">이용 조건</h2>
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

        <h2 className="form-section-title">사진과 소개</h2>
        <div className="full">
          <div className="field">
            <span>대표 사진</span>
            {form.imagePath && !previewUrl && (
              <div style={{ marginBottom: 12 }}>
                <p style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 6 }}>현재 대표 사진</p>
                <div style={{ maxWidth: 280, borderRadius: 10, overflow: 'hidden', border: '1px solid var(--line)' }}>
                  <SpacePhoto src={form.imagePath} alt="현재 등록된 공간 사진" />
                </div>
              </div>
            )}
            {previewUrl && (
              <div style={{ marginBottom: 12 }}>
                <p style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 6 }}>
                  새로 선택된 사진 미리보기 ({selectedFile?.name})
                </p>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                  <div style={{ maxWidth: 280, borderRadius: 10, overflow: 'hidden', border: '1px solid var(--line)' }}>
                    <img
                      src={previewUrl}
                      alt="새로 선택된 공간 사진 미리보기"
                      style={{ display: 'block', width: '100%', maxHeight: 200, objectFit: 'cover' }}
                    />
                  </div>
                  <Button type="button" onClick={handleClearFile}>
                    선택 취소
                  </Button>
                </div>
              </div>
            )}
            <input
              ref={fileInputRef}
              id="space-image-file"
              type="file"
              accept="image/jpeg,image/png"
              onChange={handleFileChange}
              disabled={submitting}
            />
            <span className="form-help">
              JPG, PNG 형식, 최대 5MB, 최대 4096px 해상도까지 지원합니다.
            </span>
            {fileError && (
              <span className="form-error" role="alert">
                {fileError}
              </span>
            )}
          </div>
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
            onChange={(event) => update({ status: event.target.value as SpaceStatus })}
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
