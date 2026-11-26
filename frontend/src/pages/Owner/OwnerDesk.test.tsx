import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect } from 'vitest';
import OwnerDesk from './OwnerDesk';

describe('Owner overview', () => {
  it('drills down within the selected spa and keeps the agenda on today when viewing annual analytics', () => {
    const qc = new QueryClient();
    const counts = { bookings: 75, completed: 46, confirmed: 20, pending: 4, cancelled: 9 };
    qc.setQueryData(['desk', '108', 'summary', 'YEAR', {}], {
      counts,
      previous: { ...counts, bookings: 39 },
      timezone: 'Asia/Kolkata',
      today: '2026-11-26',
      dates: { from: '2025-11-27', to: '2026-11-26' },
      money: [],
      due: [],
      services: [],
      trend: [],
      cashTrend: [],
      activeSubscriptions: 3
    });
    qc.setQueryData(
      ['desk', '108', 'records', { period: 'TODAY', view: 'APPOINTMENTS', page: 0 }],
      {
        items: [],
        more: false,
        timezone: 'Asia/Kolkata',
        dates: { from: '2026-11-26', to: '2026-11-26' }
      }
    );
    const html = renderToStaticMarkup(
      <QueryClientProvider client={qc}>
        <StaticRouter location="/owner?spa=108&period=YEAR">
          <OwnerDesk id="108" section="overview" />
        </StaticRouter>
      </QueryClientProvider>
    );
    expect(html).toContain('39 previous period');
    expect(html).toContain('All appointments · 2026-11-26');
    expect(html).toContain('spa=108&amp;period=YEAR&amp;pending=true');
    expect(html).toContain('spa=108&amp;period=TODAY');
  });
});
