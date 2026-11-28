interface StepIndicatorProps {
  currentStep: number;
  steps: string[];
}

export default function StepIndicator({ currentStep, steps }: StepIndicatorProps) {
  return (
    <ol className="appointment-progress" aria-label="Booking progress">
      {steps.map((step, index) => {
        const number = index + 1;
        const current = number === currentStep;
        return (
          <li key={step} aria-current={current ? 'step' : undefined}
            className={number < currentStep ? 'is-complete' : undefined}>
            <span className="progress-index" aria-hidden="true">{number < currentStep ? '✓' : number}</span>
            <span className="progress-name">{step}</span>
          </li>
        );
      })}
    </ol>
  );
}
