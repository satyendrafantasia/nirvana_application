import clsx from 'clsx';

interface PaymentMethodCardProps {
  label: string;
  description: string;
  method: string;
  selected: boolean;
  onSelect: (method: string) => void;
}

const PaymentMethodCard = ({ label, description, method, selected, onSelect }: PaymentMethodCardProps) => {
  return (
    <button
      onClick={() => onSelect(method)}
      className={clsx(
        'flex w-full items-start gap-3 rounded-2xl border bg-white p-4 text-left shadow-sm transition',
        selected ? 'border-primary-500 bg-primary-50' : 'border-gray-200 hover:border-primary-200'
      )}
    >
      <span className="mt-1 h-3 w-3 rounded-full border-2 border-primary-500 bg-white" />
      <div>
        <p className="text-base font-semibold text-slate-800">{label}</p>
        <p className="text-sm text-slate-500">{description}</p>
      </div>
    </button>
  );
};

export default PaymentMethodCard;
