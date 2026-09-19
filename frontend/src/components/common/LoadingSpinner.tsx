export default function LoadingSpinner({ label = '불러오는 중입니다…' }: { label?: string }) {
  return (
    <div className="empty" role="status" aria-live="polite">
      <span className="loading-indicator" aria-hidden="true" />
      <p className="muted">{label}</p>
    </div>
  );
}
