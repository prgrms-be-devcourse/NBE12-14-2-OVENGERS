import { useEffect, useRef } from 'react';

/**
 * 네이티브 <dialog> 를 사용합니다.
 * 포커스 가두기와 Esc 닫기를 브라우저가 처리하므로 직접 구현하지 않습니다.
 */
export default function Modal({ open, title, onClose, children, footer }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return undefined;
    const handleCancel = (event) => {
      event.preventDefault();
      onClose?.();
    };
    dialog.addEventListener('cancel', handleCancel);
    return () => dialog.removeEventListener('cancel', handleCancel);
  }, [onClose]);

  return (
    <dialog ref={dialogRef} aria-labelledby="modal-title">
      <h2 id="modal-title">{title}</h2>
      <div className="modal-body">{children}</div>
      <div className="modal-actions">{footer}</div>
    </dialog>
  );
}
