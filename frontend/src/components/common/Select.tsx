import { useId } from 'react';
import type { ReactNode, SelectHTMLAttributes } from 'react';
import type { SelectOption } from '../../types/api';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: ReactNode;
  options?: SelectOption[];
  error?: ReactNode;
}

export default function Select({ label, options = [], error, required, ...rest }: SelectProps) {
  const id = useId();
  return (
    <label className="field" htmlFor={id}>
      {label && (
        <span>
          {label}
          {required && <span aria-hidden="true"> *</span>}
        </span>
      )}
      <select id={id} required={required} aria-invalid={error ? 'true' : undefined} {...rest}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <span className="form-error" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
