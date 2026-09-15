import { cx } from '../../utils/format';

/** variant: default | primary | ghost | danger */
export default function Button({
  variant = 'default',
  size = 'medium',
  wide = false,
  loading = false,
  disabled = false,
  type = 'button',
  className,
  children,
  ...rest
}) {
  return (
    <button
      type={type}
      className={cx(
        variant !== 'default' && variant,
        size === 'small' && 'small',
        wide && 'wide',
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? '처리 중…' : children}
    </button>
  );
}
