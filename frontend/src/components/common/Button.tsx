import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cx } from '../../utils/format';

export type ButtonVariant = 'default' | 'primary' | 'ghost' | 'danger';

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  variant?: ButtonVariant;
  size?: 'medium' | 'small';
  wide?: boolean;
  loading?: boolean;
  type?: 'button' | 'submit' | 'reset';
  children?: ReactNode;
}

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
}: ButtonProps) {
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
