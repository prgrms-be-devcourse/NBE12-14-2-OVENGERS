import type { ReactNode } from 'react';
import type { Meta, Tone } from '../../types/api';
import { cx } from '../../utils/format';

export default function Badge({
  tone = 'default',
  children,
}: {
  tone?: Tone;
  children: ReactNode;
}) {
  return <span className={cx('badge', tone !== 'default' && tone)}>{children}</span>;
}

/** enums.ts 의 META 객체({ label, tone })를 그대로 넘겨 쓰는 단축 컴포넌트 */
export function MetaBadge({
  meta,
  fallback = '-',
}: {
  meta: Meta | undefined;
  fallback?: ReactNode;
}) {
  if (!meta) return <Badge tone="gray">{fallback}</Badge>;
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}
