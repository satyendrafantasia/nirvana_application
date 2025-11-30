import { InputHTMLAttributes } from 'react';
import clsx from 'clsx';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  helperText?: string;
};

const Input = ({ label, helperText, className, ...props }: InputProps) => {
  return (
    <label className="flex w-full flex-col gap-1 text-sm font-medium text-slate-700">
      {label}
      <input
        className={clsx(
          'w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-slate-800 placeholder:text-slate-400 shadow-sm focus:border-primary-400 focus:outline-none focus:ring-2 focus:ring-primary-100',
          className
        )}
        {...props}
      />
      {helperText && <span className="text-xs font-normal text-slate-500">{helperText}</span>}
    </label>
  );
};

export default Input;
