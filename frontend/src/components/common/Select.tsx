import { SelectHTMLAttributes } from 'react';
import clsx from 'clsx';

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label?: string;
};

const Select = ({ label, className, children, ...props }: SelectProps) => {
  return (
    <label className="flex w-full flex-col gap-1 text-sm font-medium text-slate-700">
      {label}
      <select
        className={clsx(
          'w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-primary-400 focus:outline-none focus:ring-2 focus:ring-primary-100',
          className
        )}
        {...props}
      >
        {children}
      </select>
    </label>
  );
};

export default Select;
