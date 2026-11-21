import { NavLink } from 'react-router-dom';
import { features } from '../../features/model';
import { canOpen, isAdmin, isStaff } from '../../auth/roles';
import { useAuth } from '../../hooks/useAuth';

/**
 * The account menu: only what a guest manages about themselves. Other customer pages stay
 * reachable where they belong: maps and nearby search under "Discover nearby", availability in
 * the booking flow, package history in My Packages and corporate benefits in Account details.
 */
export const ACCOUNT_MENU: { to: string; label: string }[] = [
  { to: '/me/settings', label: 'Profile & password' },
  { to: '/features/notifications', label: 'Notifications' },
  { to: '/features/messages', label: 'Help & messages' },
  { to: '/features/payments', label: 'Payments & refunds' },
  { to: '/features/reviews', label: 'My reviews' },
  { to: '/features/privacy', label: 'Privacy & data' }
];

const itemClass = ({ isActive }: { isActive: boolean }) =>
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
      {(isAdmin(user.roles) ? (['staff', 'admin'] as const) : []).map((access) => {
        const items = features.filter((f) => f.access === access && canOpen(access, user.roles));
        if (!items.length) return null;
        return (
          <Menu key={access} label={access === 'staff' ? 'Manage spa' : 'Administration'}>
            {items.map((f) => (
              <NavLink key={f.id} to={`/features/${f.id}`} onClick={close} className={itemClass}>
                {f.title}
              </NavLink>
            ))}
            {access === 'staff' && isStaff(user.roles) && (
              <NavLink to="/staff/media" onClick={close} className={itemClass}>
                Spa photos & media
              </NavLink>
            )}
          </Menu>
        );
      })}
    </>
  );
}
