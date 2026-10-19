import { it, expect } from 'vitest';
import { features, buildRequest } from './model';
import { canOpen } from '../auth/roles';
it('hides staff and admin routes from customers and anonymous users', () => {
  for (const roles of [[], ['ROLE_USER'], ['ROLE_CUSTOMER']]) {
    expect(canOpen('staff', roles)).toBe(false);
    expect(canOpen('admin', roles)).toBe(false);
  }
  expect(canOpen('staff', ['ROLE_SPA_OWNER'])).toBe(true);
  expect(canOpen('admin', ['ROLE_SPA_OWNER'])).toBe(false);
  expect(canOpen('admin', ['ROLE_PLATFORM_ADMIN'])).toBe(true);
});
it('submits refund amounts in minor units and uses the selected booking route', () => {
  const op = features
    .find((f) => f.id === 'payments')!
    .actions.find((a) => a.title === 'Request refund')!;
  const request = buildRequest(op, { bookingId: '7', amountCents: '120.50', reason: 'Cancelled' });
  expect(request.url).toBe('/api/bookings/7/refunds');
  expect(request.data).toMatchObject({ amountCents: 12050, reason: 'Cancelled' });
});
it('encodes path references and omits unrelated remembered fields', () => {
  const op = features
    .find((f) => f.id === 'messages')!
    .actions.find((a) => a.title === 'Send a reply')!;
  const request = buildRequest(op, { threadId: '7', content: 'Hello', spaId: '1' });
  expect(request.url).toBe('/api/messages/threads/7/messages');
  expect(request.data).toEqual({ content: 'Hello' });
});
it('requires an explicit spa before querying scoped analytics', () => {
  const op = features.find((f) => f.id === 'analytics')!.actions[0];
  expect(() => buildRequest(op, {})).toThrow('spa');
});
