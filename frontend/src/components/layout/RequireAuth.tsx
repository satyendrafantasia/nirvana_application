import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import Spinner from '../common/Spinner';

interface RequireAuthProps {
  children: React.ReactNode;
  redirectTo?: string;
}

export const RequireAuth = ({ children, redirectTo = '/auth/login' }: RequireAuthProps) => {
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
    const redirectParam = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`${redirectTo}?redirect=${redirectParam}`} replace />;
  }

  return <>{children}</>;
};
