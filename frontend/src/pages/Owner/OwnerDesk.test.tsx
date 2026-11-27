import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
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

const base = {
  counts: { bookings: 3, completed: 1, confirmed: 2, pending: 0, cancelled: 0 },
  previous: { bookings: 1, completed: 0, confirmed: 1, pending: 0, cancelled: 0 },
  timezone: 'Asia/Kolkata',
  today: '2026-11-27',
  dates: { from: '2026-11-27', to: '2026-11-27' },
  due: [],
  services: [],
  trend: [],
  arrivals: 2,
  conflicts: 1,
  activeSubscriptions: 0
};
const agenda = (items: unknown[]) => ({
  items,
  more: false,
  timezone: 'Asia/Kolkata',
  dates: { from: '2026-11-27', to: '2026-11-27' }
});
const render = (summary: unknown, items: unknown[], owner: boolean) => {
  const qc = new QueryClient();
  qc.setQueryData(['desk', '7', 'summary', 'TODAY', {}], summary);
  qc.setQueryData(['desk', '7', 'records', { period: 'TODAY', view: 'APPOINTMENTS', page: 0 }], agenda(items));
  return renderToStaticMarkup(
    <QueryClientProvider client={qc}>
      <StaticRouter location="/owner?spa=7">
        <OwnerDesk id="7" section="overview" owner={owner} />
      </StaticRouter>
    </QueryClientProvider>
  );
};
const partPaid = {
  id: 1,
  reference: 'REF1',
  customer: '+919876543210',
  service: 'Swedish',
  status: 'CONFIRMED',
  payment: 'DUE_AT_SPA',
  currency: 'INR',
  payable: 150000,
  deskPaid: 50000,
  deskTips: 10000,
  deskReturned: 0,
  deskCollectedUtc: new Date().toISOString(),
  startUtc: new Date(Date.now() + 3600e3).toISOString(),
  endUtc: new Date(Date.now() + 7200e3).toISOString(),
  guests: 1,
  serviceId: 1
};

describe('Owner and front desk', () => {
  it('splits desk money from online money held by Nirvana and estimates the payout', () => {
    const html = render(
      {
        ...base,
        access: 'OWNER',
        cashTrend: [],
        money: [
          { currency: 'INR', channel: 'DESK', collected: 50000, refunds: 0, tips: 10000 },
          { currency: 'INR', channel: 'ONLINE', collected: 200000, refunds: 0, tips: 0 }
        ],
        payout: [{ currency: 'INR', online: 200000, commission: 20000, commissionPct: 10, payout: 180000 }]
      },
      [partPaid],
      true
    );
    expect(html).toContain('Taken at your desk');
    expect(html).toContain('held by Nirvana until payout');
    expect(html).toContain('Expected payout from Nirvana');
    expect(html).toContain('1 scheduling clash today');
    expect(html).toContain('2 still to arrive today');
    // A part payment shows what was taken and what is still due; the owner may void it today.
    expect(html).toContain('paid at desk');
    expect(html).toContain('Record next payment');
    expect(html).toContain('Void last desk payment');
  });
  it('shows front-desk staff the day without the takings, voids or returns', () => {
    const html = render({ ...base, access: 'DESK' }, [partPaid], false);
    expect(html).not.toContain('Collections &amp; refunds');
    expect(html).not.toContain('Expected payout');
    expect(html).toContain('Record next payment');
    expect(html).not.toContain('Void last desk payment');
  });
});
