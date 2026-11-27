import { NavLink } from 'react-router-dom';
import { isAdmin } from '../../auth/roles';
import { useAuth } from '../../hooks/useAuth';

/**
 * The account menu: only what a guest manages about themselves. Other customer pages stay
 * reachable where they belong: maps and nearby search under "Discover nearby", availability in
 * the booking flow, package history in My Packages and corporate benefits in Account details.
 */
export const ACCOUNT_MENU: { to: string; label: string }[] = [
  { to: '/me/settings', label: 'Profile, points & referrals' },
  { to: '/gift-cards', label: 'Gift cards' },
  { to: '/features/notifications', label: 'Notifications' },
  { to: '/support', label: 'Help & messages' },
  { to: '/features/payments', label: 'Payments & refunds' },
  { to: '/features/reviews', label: 'My reviews' },
  { to: '/features/privacy', label: 'Privacy & data' }
];

/**
 * One admin menu, grouped by job. It replaces the separate "Manage spa" and "Administration"
 * menus and the loose "Import cities" and "Listing claims" links in the top bar.
 */
export const ADMIN_MENU: { group: string; items: { to: string; label: string }[] }[] = [
  {
    group: 'Review',
    items: [
      { to: '/admin/spa-leads', label: 'Spa leads' },
      { to: '/admin/listing-claims', label: 'Listing claims' },
      { to: '/admin/support', label: 'Support inbox' },
      { to: '/features/administration', label: 'Approvals, refunds & support' }
    ]
  },
  {
    group: 'Spas',
    items: [
      { to: '/features/spa-management', label: 'Spa profiles' },
      { to: '/features/spa-settings', label: 'Spa settings & hours' },
      { to: '/features/calendar', label: 'Booking calendar' },
      { to: '/features/analytics', label: 'Analytics' },
      { to: '/staff/media', label: 'Photos & media' },
      { to: '/admin/city-discovery', label: 'Import cities from Google' }
    ]
  },
  {
    group: 'Business',
    items: [
      { to: '/features/corporates', label: 'Corporate partners' },
      { to: '/admin/payouts', label: 'Spa payouts' },
      { to: '/features/coupons', label: 'Coupons' },
      { to: '/admin/gift-cards', label: 'Gift cards' },
      { to: '/features/content', label: 'Help content & notifications' }
    ]
  },
  { group: 'Tools', items: [{ to: '/features/files', label: 'Stored files' }] }
];

const itemClass =({ isActive }: { isActive: boolean }) =>
  `block rounded-lg px-3 py-2 hover:bg-primary-50 ${isActive ? 'text-primary-700 bg-primary-50' : ''}`;
const close = (e: React.MouseEvent<HTMLElement>) =>
  e.currentTarget.closest('details')?.removeAttribute('open');

function Menu({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <details name="nirvana-navigation" className="relative">
      <summary className="cursor-pointer px-2 py-1">{label}</summary>
      <div className="absolute right-0 top-full z-50 mt-2 w-64 max-h-[70vh] overflow-auto rounded-xl border bg-white p-2 shadow-xl">
        {children}
      </div>
    </details>
  );
}

export default function FeatureMenu() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <>
      <Menu label="My account">
        {ACCOUNT_MENU.map((item) => (
          <NavLink key={item.to} to={item.to} onClick={close} className={itemClass}>
            {item.label}
          </NavLink>
        ))}
      </Menu>
      {/* Spa owners manage everything from the Owner dashboard; this menu is for admins working across spas. */}
      {isAdmin(user.roles) && (
        <Menu label="Admin">
          {ADMIN_MENU.map(({ group, items }) => (
            <div key={group} className="border-t pt-1 first:border-t-0 first:pt-0">
              <p className="px-3 pb-0.5 pt-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {group}
              </p>
              {items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  onClick={close}
                  className={(state) => itemClass(state).replace('py-2', 'py-1.5')}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </Menu>
      )}
    </>
  );
}
