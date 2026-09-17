/** 열쇠 글리프. 색은 currentColor 를 따릅니다(모의 출입 단말 등에서 사용). */
export default function KeyGlyph({ className = 'icon' }) {
  return (
    <svg className={className} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M4 24a10 10 0 1 0 20 0 10 10 0 1 0-20 0m5.8 0a4.2 4.2 0 1 1 8.4 0 4.2 4.2 0 1 1-8.4 0"
      />
      <path
        fill="currentColor"
        d="M21 20.6h14.6a3.4 3.4 0 0 1 0 6.8H21zM26.2 25.8h3.6v4.2a1.8 1.8 0 0 1-3.6 0zM32.1 25.8h3.6v5.9a1.8 1.8 0 0 1-3.6 0z"
      />
    </svg>
  );
}
