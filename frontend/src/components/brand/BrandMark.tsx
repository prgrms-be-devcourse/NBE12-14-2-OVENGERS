/**
 * Slot Key 브랜드 마크.
 *
 * 원본은 public/brand-mark.svg 이고 파비콘(app/icon.svg)과 같은 도형입니다.
 * 그라디언트 id 충돌을 피하려고 인라인 SVG 대신 이미지로 불러옵니다.
 */
export default function BrandMark({
  size = 40,
  className,
}: {
  size?: number;
  className?: string;
}) {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src="/brand-mark.svg"
      alt=""
      aria-hidden="true"
      width={size}
      height={size}
      className={className}
    />
  );
}
