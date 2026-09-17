export default function LoadingSpinner({ label = '불러오는 중입니다…' }) {
  return (
    <div className="empty" role="status" aria-live="polite">
      <p className="muted">{label}</p>
    </div>
  );
}
