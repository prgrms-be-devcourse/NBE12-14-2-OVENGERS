import Modal from './Modal';
import Button from './Button';

/**
 * 되돌릴 수 없는 동작(취소, 재발급, 정지) 앞에 한 단계를 둡니다.
 * confirmVariant 로 위험한 동작을 시각적으로 구분합니다.
 */
export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '확인',
  cancelLabel = '닫기',
  confirmVariant = 'primary',
  loading = false,
  onConfirm,
  onClose,
  children,
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button variant={confirmVariant} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {description && <p>{description}</p>}
      {children}
    </Modal>
  );
}
