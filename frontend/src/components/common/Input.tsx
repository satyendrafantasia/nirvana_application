import { InputHTMLAttributes, useId, useState } from 'react';
import clsx from 'clsx';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  helperText?: string;
};

const Input = ({ label, helperText, className, ...props }: InputProps) => {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState('');
  const password = props.type === 'password';
  return (
    <div className="flex min-w-0 w-full flex-col gap-1 text-sm font-medium text-slate-700">
      <label htmlFor={props.id || id}>{label}{props.required && ' *'}</label>
      <div className="relative">
        <input {...props} id={props.id || id} type={password && visible ? 'text' : props.type}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || helperText ? id + '-hint' : props['aria-describedby']}
          onInvalid={e => { setError(e.currentTarget.validationMessage); props.onInvalid?.(e); }}
          onChange={e => { if (error) setError(e.target.validationMessage); props.onChange?.(e); }}
          className={clsx('min-h-11 w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-slate-800 shadow-sm focus:ring-2 focus:ring-primary-400', password && 'pr-16', className)} />
        {password && <button type="button" aria-label={(visible ? 'Hide ' : 'Show ') + label}
          aria-pressed={visible} className="absolute inset-y-0 right-0 px-3 text-xs" onClick={() => setVisible(!visible)}>{visible ? 'Hide' : 'Show'}</button>}
      </div>
      {(error || helperText) && <span id={id + '-hint'} role={error ? 'alert' : undefined} className="text-xs">{error || helperText}</span>}
    </div>
  );
};
export default Input;
