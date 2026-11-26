import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import HealthForm, { readyToSubmit, startingAnswers } from './HealthForm';
import { HealthFormView } from '../../pages/Owner/HealthFormPanel';
import { GuestHealthForm, blankAnswers } from '../../api/healthFormApi';

const prefilled: GuestHealthForm = {
  bookingId: 7,
  enabled: true,
  editable: true,
  terms: 'Spa terms',
  questions: [{ id: 3, prompt: 'Any sunburn?' }],
  status: 'NONE',
  prefilled: true,
  answers: { ...blankAnswers(), pregnant: false, recentInjuryOrSurgery: false, skinCondition: false, heartCondition: false, medications: 'Thyroxine' }
};

describe('Guest health form', () => {
  it('carries over the last answers and needs them confirmed before submitting', () => {
    const a = startingAnswers(prefilled);
    expect(a.medications).toBe('Thyroxine');
    expect(a.custom).toEqual([{ questionId: 3, prompt: 'Any sunburn?', answer: '' }]);
    expect(readyToSubmit(a, true, true, false)).toBe(false);
    expect(readyToSubmit(a, true, true, true)).toBe(true);
    expect(readyToSubmit(a, false, false, false)).toBe(false);
    expect(readyToSubmit(blankAnswers(), true, false, false)).toBe(false);
  });

  it('shows the carried-over notice and the spa terms', () => {
    const qc = new QueryClient();
    qc.setQueryData(['health-form', '7'], prefilled);
    const html = renderToStaticMarkup(
      <QueryClientProvider client={qc}>
        <HealthForm bookingId={7} onDone={() => undefined} />
      </QueryClientProvider>
    );
    expect(html).toContain('from your last visit');
    expect(html).toContain('These answers are still');
    expect(html).toContain('Spa terms');
    expect(html).toContain('Skip for now');
  });
});

describe('Health form on the appointment', () => {
  it('flags what needs attention', () => {
    const html = renderToStaticMarkup(
      <HealthFormView
        form={{
          enabled: true,
          status: 'SUBMITTED',
          attention: ['Pregnant', 'High blood pressure or heart condition'],
          answers: { ...blankAnswers(), pregnant: true, heartCondition: true, recentInjuryOrSurgery: false, skinCondition: false }
        }}
      />
    );
    expect(html).toContain('Needs attention: Pregnant · High blood pressure or heart condition');
  });
});
