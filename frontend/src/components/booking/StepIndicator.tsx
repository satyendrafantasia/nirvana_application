import clsx from 'clsx';

interface StepIndicatorProps {
  currentStep: number;
  steps: string[];
}

const StepIndicator = ({ currentStep, steps }: StepIndicatorProps) => {
  return (
    <div className="flex items-center gap-3 overflow-x-auto rounded-2xl bg-white p-4 shadow-card">
      {steps.map((step, index) => {
        const stepNumber = index + 1;
        const isActive = stepNumber === currentStep;
        const isCompleted = stepNumber < currentStep;
        return (
          <div key={step} className="flex items-center gap-2">
            <span
              className={clsx(
                'flex h-8 w-8 items-center justify-center rounded-full border-2 text-sm font-semibold',
                isCompleted && 'border-primary-500 bg-primary-50 text-primary-700',
                isActive && !isCompleted && 'border-primary-500 text-primary-700',
                !isActive && !isCompleted && 'border-slate-200 text-slate-400'
              )}
            >
              {stepNumber}
            </span>
            <span className={clsx('text-sm font-medium', isActive ? 'text-primary-700' : 'text-slate-500')}>
              {step}
            </span>
            {index < steps.length - 1 && <span className="text-slate-200">—</span>}
          </div>
        );
      })}
    </div>
  );
};

export default StepIndicator;
