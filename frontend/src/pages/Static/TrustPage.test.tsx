import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import TrustPage from './TrustPage';
import { deletionDate } from '../../api/accountApi';

const render = (state?: unknown) =>
  renderToStaticMarkup(
    <StaticRouter location={{ pathname: '/privacy', state }}>
      <TrustPage kind="privacy" />
    </StaticRouter>
  );

describe('privacy page', () => {
  it('describes the right to a copy, deletion and the grace period', () => {
    const html = render();
    expect(html).toContain('Digital Personal Data Protection Act, 2023');
    expect(html).toContain('Download my data');
    expect(html).toContain('mailto:contact@nirvana-spa.in');
    expect(html).toContain('href="/support"');
    expect(html).toContain('deleted 7 days later');
    expect(html).toContain('sign in again before then');
    expect(html).toContain('tax and');
    expect(html).not.toContain('Your account will be deleted on');
  });

  it('confirms the date right after a deletion request', () => {
    const html = render({ deletionDate: '2026-12-04T10:00:00+05:30' });
    expect(html).toContain('Your account will be deleted on ' + deletionDate('2026-12-04T10:00:00+05:30'));
    expect(deletionDate('2026-12-04T10:00:00+05:30')).toMatch(/December 2026/);
  });
});
