import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import Button from '../common/Button';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <header className="sticky top-0 z-30 bg-white/80 backdrop-blur border-b border-gray-100 shadow-sm">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-primary-700">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-2xl bg-primary-100 text-primary-700 font-bold">
            NW
          </span>
          Nirvana Wellness
        </Link>
        <nav className="hidden items-center gap-6 text-sm font-medium text-slate-700 md:flex">
          <NavLink to="/" className={({ isActive }) => (isActive ? 'text-primary-600' : '')}>
            Home
          </NavLink>
          <NavLink to="/spas" className={({ isActive }) => (isActive ? 'text-primary-600' : '')}>
            Browse Spas
          </NavLink>
          <NavLink to="/me/bookings" className={({ isActive }) => (isActive ? 'text-primary-600' : '')}>
            My Bookings
          </NavLink>
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
