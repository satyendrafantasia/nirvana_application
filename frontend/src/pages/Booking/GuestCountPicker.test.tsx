import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import GuestCountPicker from './GuestCountPicker';

const render = (value: number) => renderToStaticMarkup(<GuestCountPicker value={value} onChange={() => undefined} />);

describe('choosing who is coming', () => {
  it('offers just me, a couple and a group of up to six', () => {
    const html = render(1);
    expect(html).toContain('role="radiogroup"');
    expect(html.match(/role="radio"/g)).toHaveLength(3);
    expect(html).toContain('Just me');
    expect(html).toContain('Couple');
    expect(html).toContain('Group (up to 6)');
    expect(html).toMatch(/aria-checked="true"[^>]*>.*Just me/);
    expect(html).not.toContain('<select');
  });

  it('marks a couple as two guests', () => {
    const html = render(2);
    expect(html).toMatch(/aria-checked="true"[^>]*>.*Couple/);
    expect(html).not.toContain('<select');
  });

  it('asks how many for a group, from three to six', () => {
    const html = render(4);
    expect(html).toMatch(/aria-checked="true"[^>]*>.*Group \(up to 6\)/);
    const options = [...html.matchAll(/<option value="(\d)"/g)].map((m) => Number(m[1]));
    expect(options).toEqual([3, 4, 5, 6]);
    expect(html).toMatch(/<option value="4" selected="">4 guests/);
  });
});
