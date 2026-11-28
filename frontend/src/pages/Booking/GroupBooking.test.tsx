import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import GroupBooking, { GuestEditor, resize } from './GroupBooking';
import { findMember, getGroupTimes, guestRequests, readyForTimes } from '../../api/groupApi';
import type { Service } from '../../types/spa';

const post = vi.fn();
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, default: { ...actual.default, post: (...args: unknown[]) => post(...args) } };
});

const services: Service[] = [
  { id: '1', name: 'Deep Tissue Massage', durationMinutes: 60, price: 1200, currency: 'INR' },
  { id: '2', name: 'Hydrating Facial', durationMinutes: 45, price: 800, currency: 'INR' }
];

function withData(node: JSX.Element, setup: (client: QueryClient) => void = () => undefined) {
  const client = new QueryClient();
  setup(client);
  return renderToStaticMarkup(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe('choosing a treatment and therapist per guest', () => {
  beforeEach(() => post.mockReset());

  it('starts every guest on the booker’s treatment and keeps edits when the group grows or shrinks', () => {
    const two = resize([], 2, '1');
    expect(two.map((g) => g.serviceId)).toEqual(['1', '1']);
    const edited = [{ ...two[0], name: 'Asha' }, { ...two[1], name: 'Ravi', serviceId: '2' }];
    const four = resize(edited, 4, '1');
    expect(four).toHaveLength(4);
    expect(four[1]).toEqual(edited[1]);
    expect(resize(four, 2, '1')).toEqual(edited);
  });

  it('asks for times only when every guest has a treatment and no therapist is named twice', () => {
    expect(readyForTimes([{ name: '', serviceId: '1' }])).toBe(false);
    expect(readyForTimes([{ name: '', serviceId: '1' }, { name: 'Ravi' }])).toBe(false);
    expect(readyForTimes([{ name: '', serviceId: '1', preference: '7' }, { name: 'Ravi', serviceId: '2', preference: '7' }])).toBe(false);
    expect(readyForTimes([{ name: '', serviceId: '1', preference: 'type:Female' }, { name: 'Ravi', serviceId: '1', preference: 'type:Female' }])).toBe(true);
  });

  it('sends each guest’s own service and therapist when looking for times that fit everyone', async () => {
    post.mockResolvedValue({ data: [{ startTs: '2027-01-04T11:00:00Z', endTs: '2027-01-04T12:00:00Z' }] });
    const guests = [
      { name: '', serviceId: '1', preference: '7' },
      { name: 'Ravi', serviceId: '2', preference: 'type:Female' },
      { name: 'Kiran', serviceId: '1' }
    ];
    expect(guestRequests(guests)).toEqual([
      { serviceId: 1, therapistId: 7, therapistType: undefined },
      { serviceId: 2, therapistId: undefined, therapistType: 'Female' },
      { serviceId: 1, therapistId: undefined, therapistType: undefined }
    ]);
    const times = await getGroupTimes('3', '2027-01-04', guests);
    expect(times).toHaveLength(1);
    expect(post).toHaveBeenCalledWith('/api/spas/3/group-availability', { date: '2027-01-04', guests: guestRequests(guests) });
  });

  it('shows one editor per guest, with names asked for everyone but the booker', () => {
    const html = withData(<GroupBooking spaId="3" spaName="Calm Spa" size={3} firstService="1" onBack={() => undefined} />, (c) => {
      c.setQueryData(['booking-services', '3'], services);
      c.setQueryData(['side-by-side', '3'], { '1': 2 });
      c.setQueryData(['payment-options'], { online: true });
    });
    expect(html).toContain('Book for 3 guests');
    expect(html).toContain('aria-label="You"');
    expect(html).toContain('aria-label="Guest 2"');
    expect(html).toContain('aria-label="Guest 3"');
    expect(html.match(/Choose a treatment/g)).toHaveLength(3);
    expect(html).toContain('Deep Tissue Massage · 60 min · couples room');
    expect(html).not.toContain('Hydrating Facial · 45 min · couples room');
    // Guests 2 and 3 have no names yet, so times cannot be looked up.
    expect(html).toMatch(/disabled=""[^>]*>Find times for everyone/);
  });

  it('lists the spa’s therapists and labels for that guest’s treatment', () => {
    const html = withData(
      <GuestEditor index={1} spaId="3" guest={{ name: 'Ravi', serviceId: '2', preference: '9' }} services={services} onChange={() => undefined} />,
      (c) => c.setQueryData(['therapist-directory', '3', '2'], { named: [{ id: 9, name: 'Maya' }, { id: 10, name: 'Neha' }], labels: ['Female'] })
    );
    expect(html).toContain('Anyone available');
    expect(html).toContain('<option value="type:Female">');
    expect(html).toMatch(/<option value="9" selected="">Maya/);
    expect(html).toContain('Phone (optional)');
    expect(html).toMatch(/<option value="2" selected="">Hydrating Facial/);
  });

  it('finds which group and seat a booking belongs to', () => {
    const groups = [{ groupId: 4, members: [{ bookingId: 11, position: 1 }, { bookingId: 12, position: 2 }] }];
    expect(findMember(groups, '12')?.member.position).toBe(2);
    expect(findMember(groups, 13)).toBeUndefined();
  });
});
