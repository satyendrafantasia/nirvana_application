import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import routes from '../../routes/AppRouter.tsx?raw';
import { isAppRoute } from '../../seo/routes';
import SpaBookingReply, { headline, type SpaBookingView } from './SpaBookingReply';

const booking: SpaBookingView = {
  reference: 'AB12CD34EF56',
  spaName: 'Lotus Spa',
  service: 'Swedish Massage',
  when: 'Tue 25 Nov, 7:00 PM',
  guests: 2,
  guestName: 'Asha',
  payment: 'guest pays Rs 1,999 at the spa',
  status: 'PENDING',
  bookingStatus: 'CONFIRMED'
};

const render = (view: SpaBookingView) => {
  const qc = new QueryClient();
  qc.setQueryData(['spa-booking-reply', 'tok123'], view);
  return renderToStaticMarkup(
    <QueryClientProvider client={qc}>
      <StaticRouter location="/b/tok123">
        <Routes>
          <Route path="/b/:token" element={<SpaBookingReply />} />
        </Routes>
      </StaticRouter>
    </QueryClientProvider>
  );
};

describe('the page a spa opens from its booking SMS', () => {
  it('is open without signing in', () => {
    expect(routes).toContain('<Route path="/b/:token" element={<SpaBookingReply />} />');
    expect(isAppRoute('/b/tok123')).toBe(true);
  });
  it('shows the booking and asks the spa to confirm or decline', () => {
    const html = render(booking);
    expect(html).toContain('New booking. Can you take it?');
    expect(html).toContain('Tue 25 Nov, 7:00 PM');
    expect(html).toContain('Asha and 1 more');
    expect(html).toContain('Guest pays Rs 1,999 at the spa');
    expect(html).toContain('Confirm booking');
    expect(html).toContain('Decline');
    expect(html).toContain('If you don&#x27;t reply, the booking stays');
  });
  it('lets a spa that already confirmed still back out', () => {
    const html = render({ ...booking, status: 'CONFIRMED' });
    expect(html).toContain('You confirmed this booking');
    expect(html).not.toContain('Confirm booking');
    expect(html).toContain('We can&#x27;t take it after all');
  });
  it('offers nothing to do once the booking is cancelled', () => {
    const html = render({ ...booking, status: 'DECLINED', bookingStatus: 'CANCELLED' });
    expect(html).toContain('You declined this booking');
    expect(html).not.toContain('Confirm booking');
    expect(headline({ ...booking, status: 'CLOSED', bookingStatus: 'CANCELLED' })).toContain('was cancelled');
    expect(headline({ ...booking, status: 'AUTO_CONFIRMED' })).toContain('reply is overdue');
  });
});
