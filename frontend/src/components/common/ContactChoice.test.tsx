import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import ContactChoice from './ContactChoice';

const html = (mode: 'FIREBASE' | 'SMS' | 'OFF' | undefined, kind: 'email' | 'phone') =>
  renderToStaticMarkup(
    <ContactChoice mode={mode} kind={kind} onKind={() => {}} value="" onChange={() => {}} />
  );

describe('email or mobile for a code', () => {
  it('offers a mobile number when the site can send phone codes', () => {
    expect(html('FIREBASE', 'phone')).toContain('Mobile number');
    expect(html('FIREBASE', 'phone')).toContain('type="tel"');
    expect(html('SMS', 'email')).toContain('role="radiogroup"');
  });
  it('shows only email when phone codes are off', () => {
    const off = html('OFF', 'phone');
    expect(off).not.toContain('radiogroup');
    expect(off).toContain('type="email"');
  });
});
