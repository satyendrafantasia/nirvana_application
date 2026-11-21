import { lazy, Suspense, useEffect, useState } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { isStaff, isAdmin, ownsSpas } from '../../auth/roles';
const FeatureMenu = lazy(() => import('./FeatureMenu'));
import Button from '../common/Button';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [open,setOpen] = useState(false);
  const {pathname} = useLocation();
  useEffect(() => setOpen(false), [pathname]);
  return (
    <header className="site-header sticky top-0 z-30">
      <div className="nav-shell mx-auto flex max-w-6xl items-center justify-between flex-wrap gap-3 px-4 py-3">
        <Link
          to="/"
          className="brand flex items-center gap-2 text-lg font-semibold text-primary-700"
        >
          <span className="brand-mark inline-flex h-9 w-9 items-center justify-center font-bold">
            n.
          </span>
          Nirvana Wellness
        </Link>
        <button className="nav-toggle rounded-xl border px-4 py-2" type="button" aria-controls="site-navigation" aria-expanded={open} onClick={() => setOpen(!open)}>Menu</button>
        <nav id="site-navigation" data-expanded={open} onClick={e => { if ((e.target as Element).closest("a")) setOpen(false); }} className="main-nav flex flex-wrap items-center gap-3 text-sm font-medium text-slate-700">
          <NavLink to="/" className={({ isActive }) => (isActive ? 'text-primary-600' : '')}>
            Home
          </NavLink>
          <NavLink to="/spas" className={({ isActive }) => (isActive ? 'text-primary-600' : '')}>
            Browse Spas
          </NavLink>
          <NavLink
            to="/me/bookings"
            className={({ isActive }) => (isActive ? 'text-primary-600' : '')}
          >
            My Bookings
          </NavLink>
          <NavLink
            to="/packages"
            className={({ isActive }) => (isActive ? 'text-primary-600' : '')}
          >
            Packages
          </NavLink>
          <NavLink
            to="/me/packages"
            className={({ isActive }) => (isActive ? 'text-primary-600' : '')}
          >
            My Packages
          </NavLink>
          {/* Admin-only accounts own no spa: their tools are in the Admin menu instead. */}
          {!(isAdmin(user?.roles) && !ownsSpas(user?.roles)) && (
            <NavLink
              to={isStaff(user?.roles) ? '/owner' : '/owner/login'}
              className="text-primary-700"
            >
              {isStaff(user?.roles) ? 'Owner dashboard' : 'For spa owners'}
            </NavLink>
          )}
          <NavLink to="/discover">Discover nearby</NavLink>
          {user && <Suspense fallback={null}><FeatureMenu /></Suspense>}
        </nav>
        <div className="flex items-center gap-3">
          {user ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-sm text-slate-700 md:block">Hi, {user.firstName}</span>
              <Button
                variant="ghost"
                onClick={() => {
                  logout();
                  navigate('/');
                }}
              >
                Logout
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="ghost" as={Link} to="/auth/login">
                Login
              </Button>
              <Button as={Link} to="/auth/register">
                Sign up
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Navbar;
