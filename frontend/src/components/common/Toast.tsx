import { useEffect } from 'react';

export interface ToastProps {
  message: string | null;
  onClose?: () => void;
  duration?: number;
}

/** 동작 결과를 알리는 짧은 안내. 스크린리더에도 전달되도록 role="status" 를 씁니다. */
export default function Toast({ message, onClose, duration = 3000 }: ToastProps) {
  useEffect(() => {
    if (!message) return undefined;
    const timer = setTimeout(() => onClose?.(), duration);
    return () => clearTimeout(timer);
  }, [message, duration, onClose]);

  if (!message) return null;
  return (
    <div id="toast" style={{ display: 'block' }} role="status" aria-live="polite">
      {message}
    </div>
  );
}
