import { cx } from '../../utils/format';

/** tone: default | green | red | gray | cyan */
export default function Badge({ tone = 'default', children }) {
  return <span className={cx('badge', tone !== 'default' && tone)}>{children}</span>;
}

/** enums.js 의 META 객체({ label, tone })를 그대로 넘겨 쓰는 단축 컴포넌트 */
export function MetaBadge({ meta, fallback = '-' }) {
  if (!meta) return <Badge tone="gray">{fallback}</Badge>;
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}
