import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import Spinner from '../common/Spinner';

interface RequireAuthProps {
  children: React.ReactNode;
  redirectTo?: string;
  allowed?: (roles: string[]) => boolean;
}

export const RequireAuth = ({
  children,
  allowed,
  redirectTo = '/auth/login'
}: RequireAuthProps) => {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  if (!user) {
    const redirectParam = encodeURIComponent(location.pathname + location.search + location.hash);
    return <Navigate to={`${redirectTo}?redirect=${redirectParam}`} replace />;
  }

  if (allowed && !allowed(user.roles ?? []))
    return (
      <section className="mx-auto max-w-xl px-4 py-12">
        <h1 className="text-3xl font-bold">Access restricted</h1>
        <p className="my-4">Your account does not have access to this workspace.</p>
        <Link className="font-semibold underline" to="/spas">
          Browse spas
        </Link>
      </section>
    );
  return <>{children}</>;
};
