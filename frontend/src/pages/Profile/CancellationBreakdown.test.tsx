import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import CancellationBreakdown from './CancellationBreakdown';

it('shows the total refund and the portion going back to each tender', () => {
  const html = renderToStaticMarkup(
    <CancellationBreakdown
      quote={{
        currency: 'INR',
        feeCents: 125,
        refundCents: 376,
        route: 'ORIGINAL_METHOD',
        packageSession: false,
        walletFeeCents: 126,
        walletRefundCents: 375
      }}
    />
  );
  expect(html).toContain('₹7.51');
  expect(html).toContain('₹2.51');
  expect(html).toContain('Original payment method');
  expect(html).toContain('₹3.76');
  expect(html).toContain('Nirvana wallet credit returned');
  expect(html).toContain('₹3.75');
});
