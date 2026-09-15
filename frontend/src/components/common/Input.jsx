import { useId } from 'react';

export default function Input({ label, error, help, required, className, ...rest }) {
  const id = useId();
  const describedBy = [error && `${id}-error`, help && `${id}-help`].filter(Boolean).join(' ');

  return (
    <label className="field" htmlFor={id}>
      {label && (
        <span>
          {label}
          {required && <span aria-hidden="true"> *</span>}
        </span>
      )}
      <input
        id={id}
        className={className}
        required={required}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy || undefined}
        {...rest}
      />
      {help && (
        <span className="form-help" id={`${id}-help`}>
          {help}
        </span>
      )}
      {error && (
        <span className="form-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
