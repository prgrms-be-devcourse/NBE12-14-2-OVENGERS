/** 작은 화면에서도 선명하게 보이는 단색 문·키홀 심벌. */
export default function BrandMark({ size = 40, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" className={className} aria-hidden="true" focusable="false">
      <path d="M9 5h18v30H9V5Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <path d="m14 8 17-4v32l-17-4V8Z" fill="currentColor" fillOpacity=".07" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
      <circle cx="24" cy="19" r="1.7" fill="currentColor" /><path d="M24 20v3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
