import { NavLink } from 'react-router-dom';
import { features } from '../../features/model';
import { canOpen, isStaff } from '../../auth/roles';
import { useAuth } from '../../hooks/useAuth';
export default function FeatureMenu() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <>
      {['customer', 'staff', 'admin'].map((access) => {
        const items = features.filter((f) => f.access === access && canOpen(access, user.roles));
        if (!items.length) return null;
        return (
          <details key={access} className="relative">
            <summary className="cursor-pointer px-2 py-1">
              {access === 'customer'
                ? 'My account'
                : access === 'staff'
                  ? 'Manage spa'
                  : 'Administration'}
            </summary>
            <div className="absolute right-0 top-full z-50 mt-2 w-64 max-h-[70vh] overflow-auto rounded-xl border bg-white p-2 shadow-xl">
              {items.map((f) => (
                <NavLink
                  key={f.id}
                  to={`/features/${f.id}`}
                  onClick={(e) => e.currentTarget.closest('details')?.removeAttribute('open')}
                  className={({ isActive }) =>
                    `block rounded-lg px-3 py-2 hover:bg-primary-50 ${isActive ? 'text-primary-700 bg-primary-50' : ''}`
                  }
                >
                  {f.title}
                </NavLink>
              ))}
              {access === 'customer' && (
                <>
                  <NavLink className="block px-3 py-2" to="/me/settings">
                    Account details
                  </NavLink>
                  <NavLink className="block px-3 py-2" to="/auth/password">
                    Reset password
                  </NavLink>
                </>
              )}
              {access === 'staff' && isStaff(user.roles) && (
                <NavLink className="block px-3 py-2" to="/staff/media">
                  Spa photos & media
                </NavLink>
              )}
            </div>
          </details>
        );
      })}
    </>
  );
}
