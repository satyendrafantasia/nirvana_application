import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import OwnerPortal, { bookingActions, canSpaCancel, ownerTabs } from './OwnerPortal';
import Register from '../Auth/Register';
import Login from '../Auth/Login';
/** Whether a link to href is marked as the current page, whatever the attribute order. */
const isCurrent = (html: string, href: string) =>
  (html.match(/<a [^>]*>/g) || []).some((a) => a.includes(`href="${href}"`) && a.includes('aria-current="page"'));
function render(element: React.ReactNode, roles: string[] = ['ROLE_USER']) {
  return renderAt(element, '/owner', roles);
}
function renderAt(element: React.ReactNode, location: string, roles: string[] = ['ROLE_USER']) {
  const auth = {
    user: { id: '1', firstName: 'Test', lastName: 'User', email: 'test@example.test', roles },
    token: 'test',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    hydrateUser: vi.fn(),
    setUser: vi.fn(),
    acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <AuthContext.Provider value={auth}>
        <StaticRouter location={location}>{element}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}
describe('Front-desk booking actions', () => {
  const now = Date.parse('2026-11-20T10:00:00Z');
  const paths = (status: string, startTs: string) =>
    bookingActions({ status, startTs }, now).map((a) => a.path);
  it('offers nothing for an appointment far in the future', () => {
    expect(paths('CONFIRMED', '2026-11-25T10:00:00Z')).toEqual([]);
  });
  it('offers only check-in shortly before the start', () => {
    expect(paths('CONFIRMED', '2026-11-20T11:00:00Z')).toEqual(['arrive']);
  });
  it('offers check-in, completion and no-show once the appointment has started', () => {
    expect(paths('CONFIRMED', '2026-11-20T09:30:00Z')).toEqual(['arrive', 'complete', 'no-show']);
  });
  it('offers only completion after check-in', () => {
    expect(paths('ARRIVED', '2026-11-20T09:30:00Z')).toEqual(['complete']);
  });
  it('lets the spa cancel an open booking only until the appointment ends', () => {
    const endTs = '2026-11-20T10:30:00Z';
    expect(canSpaCancel({ status: 'CONFIRMED', endTs }, now)).toBe(true);
    expect(canSpaCancel({ status: 'PENDING_PAYMENT', endTs }, now)).toBe(true);
    expect(canSpaCancel({ status: 'CONFIRMED', endTs: '2026-11-20T09:59:00Z' }, now)).toBe(false);
    for (const status of ['ARRIVED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'])
      expect(canSpaCancel({ status, endTs }, now)).toBe(false);
  });
  it('offers nothing for closed or unpaid appointments', () => {
    for (const status of ['COMPLETED', 'CANCELLED', 'NO_SHOW', 'PENDING_PAYMENT'])
      expect(paths(status, '2026-11-20T09:30:00Z')).toEqual([]);
  });
});
describe('Owner entry and access boundaries', () => {
  it('offers verified owner registration without an administrator selector', () => {
    const html = render(<Register owner />);
    expect(html).toContain('Create your spa owner account');
    expect(html).toContain('/owner/login');
    expect(html).toContain('Send registration link');
    expect(html).not.toContain('value="ADMIN"');
  });
  it('links owner login to dedicated signup', () => {
    const html = render(<Login owner />);
    expect(html).toContain('Spa owner sign in');
    expect(html).toContain('/owner/signup');
  });
  it('blocks a customer from owner data screens', () => {
    const html = render(<OwnerPortal />);
    expect(html).toContain('Owner access required');
    expect(html).not.toContain('Owner workspace');
  });
  it('shows the owner workspaces', () => {
    const html = render(<OwnerPortal />, ['ROLE_USER', 'ROLE_SPA_OWNER']);
    expect(ownerTabs.map((t) => t.label)).toEqual([
      'Overview',
      'Bookings',
      'Payments',
      'Payouts',
      'Customers',
      'Analytics',
      'Schedule',
      'Services & team',
      'Packages',
      'Settings'
    ]);
    for (const tab of ownerTabs) expect(html).toContain(`href="${tab.to}"`);
    expect(isCurrent(html, '/owner')).toBe(true);
    expect(isCurrent(html, '/owner/bookings')).toBe(false);
    expect(html).not.toContain('/features/files');
  });
  it('keeps every owner page reachable through a tab or sub-tab', () => {
    const reachable = ownerTabs.flatMap((t) => t.sections);
    for (const section of ['overview', 'bookings', 'availability', 'services', 'packages', 'therapists', 'settings', 'photos', 'onboarding'])
      expect(reachable).toContain(section);
  });
  it('shows the sub-tabs of the tab you are on', () => {
    const html = renderAt(<OwnerPortal />, '/owner/packages?spa=108', ['ROLE_SPA_OWNER']);
    expect(isCurrent(html, '/owner/packages?spa=108')).toBe(true);
    expect(isCurrent(html, '/owner/services?spa=108')).toBe(false);
    const services = renderAt(<OwnerPortal />, '/owner/services', ['ROLE_SPA_OWNER']);
    expect(services).toContain('Treatments &amp; prices');
    const settings = renderAt(<OwnerPortal />, '/owner/onboarding', ['ROLE_SPA_OWNER']);
    expect(settings).toContain('Setup &amp; verification');
    expect(settings).toContain('/owner/photos');
    expect(settings).toContain('/features/spa-management');
  });
});
