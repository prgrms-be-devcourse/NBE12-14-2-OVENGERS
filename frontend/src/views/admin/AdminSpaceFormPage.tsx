'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { SpacePayload } from '../../api/adminSpaceApi';
import { createSpace, getAdminSpace, updateSpace, uploadSpaceImage } from '../../api/adminSpaceApi';
import { useAction, useAsync } from '../../hooks/useApi';
import { ROUTES } from '../../constants/routePaths';
import SpaceForm from '../../components/space/SpaceForm';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorMessage from '../../components/common/ErrorMessage';
import Button from '../../components/common/Button';
import { ApiError } from '../../api/client';
import type { MessageSource } from '../../constants/errorCodes';
import { useRouteId } from '@/hooks/useRouteId';
import { validateImageFile } from '../../utils/imageValidation';

export default function AdminSpaceFormPage({ mode = 'create' }: { mode?: 'create' | 'edit' }) {
  const spaceId = useRouteId('spaceId');
  const router = useRouter();
  const isEdit = mode === 'edit';

  // 신규 등록 시 공간 생성 성공 후 사진 업로드 실패 상태
  const [createdSpaceId, setCreatedSpaceId] = useState<number | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [createImageFailed, setCreateImageFailed] = useState(false);
  const [createImageError, setCreateImageError] = useState<MessageSource | string | null>(null);

  // 수정 시 기본 정보 PATCH 성공 후 사진 업로드 실패 상태
  const [editImageFailed, setEditImageFailed] = useState(false);
  const [editImageError, setEditImageError] = useState<MessageSource | string | null>(null);

  // 실패 패널에서 새로 선택한 대체 사진 상태 (추가 PATCH/POST 없이 PUT만 전송)
  const [replacementFile, setReplacementFile] = useState<File | null>(null);
  const [replacementFileError, setReplacementFileError] = useState<string | null>(null);
  const [replacementPreviewUrl, setReplacementPreviewUrl] = useState<string | null>(null);

  // 대체 사진 input DOM 값 초기화용 ref
  const replacementFileInputRef = useRef<HTMLInputElement>(null);

  // 동기적 업로드 in-flight 가드 (빠른 연속 클릭 차단)
  const isUploadingRef = useRef(false);

  // 재시도 진행 중 상태
  const [imageRetrying, setImageRetrying] = useState(false);
  const [resumingEdit, setResumingEdit] = useState(false);

  useEffect(() => {
    return () => {
      if (replacementPreviewUrl) {
        URL.revokeObjectURL(replacementPreviewUrl);
      }
    };
  }, [replacementPreviewUrl]);

  const fetchSpace = useCallback(
    () => (isEdit ? getAdminSpace(spaceId) : Promise.resolve(null)),
    [isEdit, spaceId],
  );
  const { data: space, loading, error, setData: setSpace } = useAsync(fetchSpace, [fetchSpace], { immediate: isEdit });

  const submit = useAction(async (payload: SpacePayload, file?: File | null) => {
    if (isEdit) {
      setEditImageFailed(false);
      setEditImageError(null);

      await updateSpace(spaceId, payload);
      if (file) {
        try {
          await uploadSpaceImage(spaceId, file);
        } catch (uploadError) {
          setPendingFile(file);
          setEditImageFailed(true);
          setEditImageError(
            uploadError instanceof ApiError
              ? uploadError
              : '새 사진 업로드 중 오류가 발생했습니다.',
          );
          return;
        }
      }
      router.push(ROUTES.adminSpaces);
    } else {
      // 이미 공간이 생성된 상태라면 중복 POST를 방지한다
      if (createdSpaceId) {
        return;
      }

      setCreateImageFailed(false);
      setCreateImageError(null);

      const created = await createSpace(payload);
      if (file) {
        try {
          await uploadSpaceImage(created.id, file);
        } catch (uploadError) {
          setCreatedSpaceId(created.id);
          setPendingFile(file);
          setCreateImageFailed(true);
          setCreateImageError(
            uploadError instanceof ApiError
              ? uploadError
              : '사진 업로드 중 오류가 발생했습니다.',
          );
          return;
        }
      }
      router.push(ROUTES.adminSpaces);
    }
  });

  const handleReplacementFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const validationError = validateImageFile(file);
    if (validationError) {
      if (replacementPreviewUrl) {
        URL.revokeObjectURL(replacementPreviewUrl);
        setReplacementPreviewUrl(null);
      }
      setReplacementFile(null);
      setReplacementFileError(validationError);
      event.target.value = '';
      if (replacementFileInputRef.current) {
        replacementFileInputRef.current.value = '';
      }
      return;
    }

    setReplacementFileError(null);
    setReplacementFile(file);
    if (replacementPreviewUrl) {
      URL.revokeObjectURL(replacementPreviewUrl);
    }
    setReplacementPreviewUrl(URL.createObjectURL(file));
  };

  const handleClearReplacementFile = () => {
    setReplacementFile(null);
    setReplacementFileError(null);
    if (replacementPreviewUrl) {
      URL.revokeObjectURL(replacementPreviewUrl);
      setReplacementPreviewUrl(null);
    }
    if (replacementFileInputRef.current) {
      replacementFileInputRef.current.value = '';
    }
  };

  const handleRetryCreateImageUpload = async () => {
    if (!createdSpaceId || !pendingFile || isUploadingRef.current || imageRetrying) return;
    try {
      isUploadingRef.current = true;
      setImageRetrying(true);
      await uploadSpaceImage(createdSpaceId, pendingFile);
      router.push(ROUTES.adminSpaces);
    } catch (err) {
      isUploadingRef.current = false;
      setImageRetrying(false);
      setCreateImageError(
        err instanceof ApiError
          ? err
          : '사진 업로드 재시도 중 오류가 발생했습니다.',
      );
    }
  };

  const handleRetryEditImageUpload = async () => {
    if (!spaceId || !pendingFile || isUploadingRef.current || imageRetrying || resumingEdit) return;
    try {
      isUploadingRef.current = true;
      setImageRetrying(true);
      await uploadSpaceImage(spaceId, pendingFile);
      router.push(ROUTES.adminSpaces);
    } catch (err) {
      isUploadingRef.current = false;
      setImageRetrying(false);
      setEditImageError(
        err instanceof ApiError
          ? err
          : '사진 업로드 재시도 중 오류가 발생했습니다.',
      );
    }
  };

  const handleUploadReplacementImage = async (targetSpaceId: number | string, isCreateFlow: boolean) => {
    if (
      !targetSpaceId ||
      !replacementFile ||
      replacementFileError ||
      isUploadingRef.current ||
      imageRetrying ||
      resumingEdit
    ) {
      return;
    }
    try {
      isUploadingRef.current = true;
      setImageRetrying(true);
      await uploadSpaceImage(targetSpaceId, replacementFile);
      if (replacementPreviewUrl) {
        URL.revokeObjectURL(replacementPreviewUrl);
      }
      router.push(ROUTES.adminSpaces);
    } catch (err) {
      isUploadingRef.current = false;
      setImageRetrying(false);
      const errPayload =
        err instanceof ApiError
          ? err
          : '새 사진 업로드 중 오류가 발생했습니다.';
      if (isCreateFlow) {
        setCreateImageError(errPayload);
      } else {
        setEditImageError(errPayload);
      }
    }
  };

  const handleResumeEditing = async () => {
    if (isUploadingRef.current || imageRetrying || resumingEdit) return;
    try {
      setResumingEdit(true);
      const freshSpace = await getAdminSpace(spaceId);
      setSpace(freshSpace);
      setPendingFile(null);
      handleClearReplacementFile();
      setEditImageFailed(false);
      setEditImageError(null);
    } catch (err) {
      setEditImageError(
        err instanceof ApiError
          ? err
          : '최신 공간 정보를 불러오지 못했습니다. 사진 업로드를 다시 시도하거나 나중에 다시 시도해주세요.',
      );
    } finally {
      setResumingEdit(false);
    }
  };

  if (isEdit && loading) return <LoadingSpinner />;
  if (isEdit && error) return <ErrorMessage error={error} />;

  return (
    <>
      <nav className="crumb" aria-label="현재 위치">
        <Link href={ROUTES.adminSpaces}>공간 관리</Link>
        <span>›</span>
        <span>{isEdit ? '공간 수정' : '공간 등록'}</span>
      </nav>

      <div className="pagehead">
        <div>
          <p className="page-kicker">OFFICE DETAILS</p>
          <h1>{isEdit ? '공간 수정' : '공간 등록'}</h1>
          <p>
            {isEdit
              ? '요금을 바꿔도 이미 확정된 예약의 금액은 유지됩니다.'
              : '공간 정보와 이용 조건을 입력해 주세요.'}
          </p>
        </div>
      </div>

      {/* 신규 등록: 공간 ID 생성 후 사진 업로드 실패 패널 (등록 폼을 대체하여 중복 등록 방지) */}
      {!isEdit && createImageFailed && createdSpaceId && (
        <div
          className="panel"
          style={{
            marginBottom: 24,
            border: '1px solid #fca5a5',
            backgroundColor: '#fef2f2',
            padding: 24,
            borderRadius: 12,
          }}
        >
          <h3 style={{ color: '#991b1b', marginBottom: 8, fontSize: '1.15rem' }}>
            공간 기본 정보 등록 완료 (대표 사진 업로드 실패)
          </h3>
          <p style={{ color: '#7f1d1d', marginBottom: 12, fontSize: '0.95rem', lineHeight: 1.5 }}>
            공간 기본 정보(ID: {createdSpaceId})가 정상적으로 등록되었습니다.
            대표 사진 업로드 중 오류가 발생했으므로, 아래에서 원래 사진으로 다시 시도하거나 새 사진을 선택하여 업로드만 진행할 수 있습니다.
          </p>
          <ErrorMessage error={createImageError} />

          {/* 새 대표 사진 선택 및 PUT-only 업로드 영역 */}
          <div
            style={{
              marginTop: 16,
              marginBottom: 16,
              padding: 16,
              border: '1px dashed #f87171',
              borderRadius: 8,
              backgroundColor: '#fffaf0',
            }}
          >
            <h4 style={{ margin: '0 0 6px 0', fontSize: '0.95rem', color: '#991b1b' }}>
              새 사진 선택 (기본 정보 추가 저장 없이 사진만 업로드)
            </h4>
            <p style={{ margin: '0 0 10px 0', fontSize: '0.85rem', color: '#7f1d1d' }}>
              해상도 초과 등으로 실패한 경우 다른 유효한 사진을 선택해 즉시 업로드할 수 있습니다. (JPG, PNG, 최대 5MB, 최대 4096px)
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <input
                ref={replacementFileInputRef}
                id="create-replacement-image"
                type="file"
                accept="image/jpeg,image/png"
                disabled={imageRetrying}
                onChange={handleReplacementFileChange}
                style={{ fontSize: '0.85rem' }}
              />
              {replacementFile && (
                <Button
                  type="button"
                  disabled={imageRetrying}
                  onClick={handleClearReplacementFile}
                >
                  선택 취소
                </Button>
              )}
            </div>
            {replacementFileError && (
              <p style={{ margin: '6px 0 0 0', color: '#b91c1c', fontSize: '0.85rem' }} role="alert">
                {replacementFileError}
              </p>
            )}
            {replacementPreviewUrl && replacementFile && !replacementFileError && (
              <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 16 }}>
                <img
                  src={replacementPreviewUrl}
                  alt="새로 선택한 사진 미리보기"
                  style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 6, border: '1px solid #cbd5e1' }}
                />
                <div>
                  <p style={{ margin: 0, fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
                    {replacementFile.name} ({(replacementFile.size / 1024).toFixed(1)} KB)
                  </p>
                  <Button
                    variant="primary"
                    loading={imageRetrying}
                    disabled={imageRetrying || Boolean(replacementFileError) || !replacementFile}
                    onClick={() => handleUploadReplacementImage(createdSpaceId, true)}
                    style={{ marginTop: 8 }}
                  >
                    새 사진으로 업로드
                  </Button>
                </div>
              </div>
            )}
          </div>

          <div className="actions" style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {pendingFile && (
              <Button
                loading={imageRetrying}
                disabled={imageRetrying}
                onClick={handleRetryCreateImageUpload}
              >
                원래 사진({pendingFile.name})으로 다시 시도
              </Button>
            )}
            <Button
              disabled={imageRetrying}
              onClick={() => router.push(`/admin/spaces/${createdSpaceId}/edit`)}
            >
              공간 수정 화면으로 이동
            </Button>
            <Button
              disabled={imageRetrying}
              onClick={() => router.push(ROUTES.adminSpaces)}
            >
              공간 목록으로 이동
            </Button>
          </div>
        </div>
      )}

      {/* 공간 수정: 기본 정보 PATCH 성공 후 새 사진 PUT 실패 알림 패널 */}
      {isEdit && editImageFailed && (
        <div
          className="panel"
          style={{
            marginBottom: 24,
            border: '1px solid #fca5a5',
            backgroundColor: '#fef2f2',
            padding: 24,
            borderRadius: 12,
          }}
        >
          <h3 style={{ color: '#991b1b', marginBottom: 8, fontSize: '1.15rem' }}>
            공간 기본 정보 수정 완료 (대표 사진 업로드 실패)
          </h3>
          <p style={{ color: '#7f1d1d', marginBottom: 12, fontSize: '0.95rem', lineHeight: 1.5 }}>
            공간 기본 정보는 정상적으로 저장되었습니다. 새 대표 사진 업로드 중 오류가 발생하였으며,
            사진 업로드가 성공하기 전까지는 기존 사진이 안전하게 유지됩니다.
            아래에서 새 사진을 선택하여 업로드만 다시 진행하거나, 원래 사진으로 다시 시도할 수 있습니다.
          </p>
          <ErrorMessage error={editImageError} />

          {/* 새 대표 사진 선택 및 PUT-only 업로드 영역 */}
          <div
            style={{
              marginTop: 16,
              marginBottom: 16,
              padding: 16,
              border: '1px dashed #f87171',
              borderRadius: 8,
              backgroundColor: '#fffaf0',
            }}
          >
            <h4 style={{ margin: '0 0 6px 0', fontSize: '0.95rem', color: '#991b1b' }}>
              새 사진 선택 (기본 정보 추가 수정 없이 사진만 업로드)
            </h4>
            <p style={{ margin: '0 0 10px 0', fontSize: '0.85rem', color: '#7f1d1d' }}>
              해상도 초과 등으로 실패한 경우 다른 유효한 사진을 선택해 즉시 업로드할 수 있습니다. (JPG, PNG, 최대 5MB, 최대 4096px)
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <input
                ref={replacementFileInputRef}
                id="edit-replacement-image"
                type="file"
                accept="image/jpeg,image/png"
                disabled={imageRetrying || resumingEdit}
                onChange={handleReplacementFileChange}
                style={{ fontSize: '0.85rem' }}
              />
              {replacementFile && (
                <Button
                  type="button"
                  disabled={imageRetrying || resumingEdit}
                  onClick={handleClearReplacementFile}
                >
                  선택 취소
                </Button>
              )}
            </div>
            {replacementFileError && (
              <p style={{ margin: '6px 0 0 0', color: '#b91c1c', fontSize: '0.85rem' }} role="alert">
                {replacementFileError}
              </p>
            )}
            {replacementPreviewUrl && replacementFile && !replacementFileError && (
              <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 16 }}>
                <img
                  src={replacementPreviewUrl}
                  alt="새로 선택한 사진 미리보기"
                  style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 6, border: '1px solid #cbd5e1' }}
                />
                <div>
                  <p style={{ margin: 0, fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
                    {replacementFile.name} ({(replacementFile.size / 1024).toFixed(1)} KB)
                  </p>
                  <Button
                    variant="primary"
                    loading={imageRetrying}
                    disabled={imageRetrying || resumingEdit || Boolean(replacementFileError) || !replacementFile}
                    onClick={() => handleUploadReplacementImage(spaceId, false)}
                    style={{ marginTop: 8 }}
                  >
                    새 사진으로 업로드
                  </Button>
                </div>
              </div>
            )}
          </div>

          <div className="actions" style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {pendingFile && (
              <Button
                loading={imageRetrying}
                disabled={imageRetrying || resumingEdit}
                onClick={handleRetryEditImageUpload}
              >
                원래 사진({pendingFile.name})으로 다시 시도
              </Button>
            )}
            <Button
              disabled={imageRetrying || resumingEdit}
              onClick={() => router.push(ROUTES.adminSpaces)}
            >
              공간 목록으로 이동
            </Button>
            <Button
              loading={resumingEdit}
              disabled={imageRetrying || resumingEdit}
              onClick={handleResumeEditing}
            >
              기본 정보 계속 수정
            </Button>
          </div>
        </div>
      )}

      {/* 신규 등록/수정 시 사진 업로드 실패 복구 중인 경우 폼을 숨겨 중복 POST/PATCH 차단 */}
      {!createImageFailed && !editImageFailed && (
        <SpaceForm
          mode={mode}
          initialValue={space ?? undefined}
          submitting={submit.loading || imageRetrying}
          error={submit.error}
          onSubmit={(payload, file) => submit.execute(payload, file).catch(() => {})}
          onCancel={() => router.push(ROUTES.adminSpaces)}
        />
      )}
    </>
  );
}
