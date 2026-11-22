import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { PaymentChoice } from './Payment';

const render = (props: Partial<Parameters<typeof PaymentChoice>[0]>) =>
  renderToStaticMarkup(<PaymentChoice value="ONLINE" online onChange={() => undefined} {...props} />);

describe('choosing how to pay', () => {
  it('offers Razorpay and pay at spa as two buttons, not a dropdown', () => {
    const html = render({});
    expect(html).not.toContain('<select');
    expect(html).toContain('role="radiogroup"');
    expect(html).toContain('Pay now with Razorpay');
    expect(html).toContain('Pay at spa');
    expect(html.match(/role="radio"/g)).toHaveLength(2);
    expect(html).toMatch(/aria-checked="true"[^>]*>.*Pay now with Razorpay/);
  });
  it('marks pay at spa when the guest picks it', () => {
    expect(render({ value: 'OFFLINE' })).toMatch(/aria-checked="true"[^>]*>.*Pay at spa/);
  });
  it('still shows Razorpay when it is not set up, as unavailable instead of failing at checkout', () => {
    const html = render({ online: false, value: 'OFFLINE' });
    expect(html).toContain('Pay now with Razorpay');
    expect(html).toMatch(/disabled=""[^>]*>.*Pay now with Razorpay/);
    expect(html).toContain('Online payment isn’t available yet');
    expect(html).toMatch(/aria-checked="true"[^>]*>.*Pay at spa/);
  });
  it('needs no payment choice when a package pays', () => {
    const html = render({ byPackage: true });
    expect(html).toContain('Paid with your package');
    expect(html).not.toContain('role="radio"');
  });
});
