import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import Payouts, { BankDetailsForm } from './Payouts';
import AdminPayouts, { AdminPayoutCard } from '../Staff/AdminPayouts';
import { ownerTabs, deskSections } from './OwnerPortal';
import { ADMIN_MENU } from '../../components/layout/FeatureMenu';
import { isAppRoute } from '../../seo/routes';
import { type Payout, period, statusLabel, validAccount, validIfsc } from '../../api/payoutApi';

const payout: Payout = {
  id: 41,
  spaId: 7,
  spaName: 'Lotus Day Spa',
  reference: 'PO-7-261123-ABCDE',
  currency: 'INR',
  status: 'FAILED',
  periodStart: '2026-11-15T18:30:00', // midnight in India, as UTC
  periodEnd: '2026-11-22T18:30:00',
  gross: 180000,
  refunds: 25000,
  commissionPct: 10,
  commission: 15500,
  net: 139500,
  provider: 'RAZORPAY_ROUTE',
  transferId: null,
  failureReason: 'Insufficient balance',
  attempts: 1,
  createdAt: '2026-11-23T03:30:00'
};

function render(node: React.ReactNode, qc = new QueryClient(), roles = ['ROLE_SPA_OWNER']) {
  const auth = {
    user: { id: '2', firstName: 'A', lastName: 'O', email: 'o@example.test', roles },
    token: 't',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    hydrateUser: vi.fn(),
    setUser: vi.fn(),
    acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <StaticRouter location="/owner/payouts?spa=7">{node}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

describe('owner payouts', () => {
  it('is an owner tab that front-desk staff do not get', () => {
    expect(ownerTabs.map((t) => t.to)).toContain('/owner/payouts');
    expect(deskSections).not.toContain('payouts');
  });

  it('shows each statement: period, online, refunds, commission, net, status and transfer', () => {
    const qc = new QueryClient();
    qc.setQueryData(['payouts', '7', 0], {
      automatic: true,
      account: {
        accountHolder: 'Lotus Wellness',
        ifsc: 'HDFC0001234',
        accountMasked: 'XXXX9012',
        businessType: 'partnership',
        routeStatus: 'LINKED',
        updatedAt: '2026-11-20T10:00:00'
      },
      upcoming: [
        {
          currency: 'INR',
          gross: 70000,
          refunds: 0,
          commissionPct: 10,
          commission: 7000,
          net: 63000,
          items: 1
        }
      ],
      items: [
        payout,
        { ...payout, id: 40, status: 'COMPLETED', transferId: 'trf_Paid123', failureReason: null }
      ],
      hasMore: false
    });
    const html = render(<Payouts spaId="7" />, qc);
    for (const text of [
      '₹1,800.00',
      '−₹250.00',
      '−₹155.00',
      '₹1,395.00',
      '10%',
      'Transfer failed',
      'Insufficient balance',
      'trf_Paid123',
      'Paid'
    ])
      expect(html).toContain(text);
    expect(html).toContain('Next payout so far: <strong>₹630.00</strong>');
    expect(html).toContain('16 Nov 2026 – 22 Nov 2026');
    expect(html).toContain('XXXX9012');
    expect(html).toContain('Ready for automatic payouts');
    expect(html).toContain('front desk is already yours');
  });

  it('asks for bank details, and never shows a stored account number', () => {
    const empty = render(<BankDetailsForm spaId="7" account={null} automatic={false} />);
    expect(empty).toContain('Account number again');
    expect(empty).toContain('IFSC');
    expect(empty).toContain('only the last four digits');
    expect(empty).toContain('Save bank account');
    const failed = render(
      <BankDetailsForm
        spaId="7"
        automatic
        account={{
          accountHolder: 'Lotus',
          ifsc: 'HDFC0001234',
          accountMasked: 'XXXX9012',
          businessType: 'llp',
          routeStatus: 'FAILED',
          routeError: 'Invalid IFSC',
          updatedAt: '2026-11-20T10:00:00'
        }}
      />
    );
    expect(failed).toContain('couldn&#x27;t set up payouts to this account: Invalid IFSC');
    expect(failed).not.toMatch(/\d{9,}/);
  });

  it('checks IFSC and account numbers the way the server does', () => {
    expect(validIfsc('hdfc0001234')).toBe(true);
    expect(validIfsc('HDFC1001234')).toBe(false);
    expect(validAccount('1234 5678 9012')).toBe(true);
    expect(validAccount('12345')).toBe(false);
    expect(statusLabel({ status: 'PENDING', transferId: null })).toBe('Waiting to be paid');
    expect(period({ periodStart: '2026-11-16T00:00:00', periodEnd: null })).toBe('—');
  });
});

describe('admin payouts', () => {
  it('is for admins only and in the admin menu', () => {
    expect(render(<AdminPayouts />)).toContain('Admin access required');
    const html = render(<AdminPayouts />, new QueryClient(), ['ROLE_ADMIN']);
    expect(html).toContain('Spa payouts');
    expect(html).toContain('Run settlement now');
    expect(ADMIN_MENU.flatMap((g) => g.items.map((i) => i.to))).toContain('/admin/payouts');
    expect(isAppRoute('/admin/payouts')).toBe(true);
  });

  it('offers a retry and a manual close-out on a failed payout, but not on a paid one', () => {
    const failed = render(<AdminPayoutCard payout={payout} />, new QueryClient(), ['ROLE_ADMIN']);
    expect(failed).toContain('Lotus Day Spa');
    expect(failed).toContain('Insufficient balance');
    expect(failed).toContain('Retry transfer');
    expect(failed).toContain('Record as paid');
    const paid = render(
      <AdminPayoutCard payout={{ ...payout, status: 'COMPLETED', transferId: 'trf_Paid123' }} />,
      new QueryClient(),
      ['ROLE_ADMIN']
    );
    expect(paid).toContain('trf_Paid123');
    expect(paid).not.toContain('Retry transfer');
  });
});
