import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { authView } from '../../api/authApi';
import { sessionClient, errorMessage } from '../../api/client';
export default function SocialLoginCallback() {
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (new URLSearchParams(location.search).has('error')) {
      setError('Social sign-in was cancelled or failed. Please try again.');
      return;
    }
    void sessionClient
      .post('/api/auth/oauth2/session')
      .then(({ data }) => {
        acceptAuth(authView(data));
        navigate('/', { replace: true });
      })
      .catch((e) => setError(errorMessage(e)));
  }, [acceptAuth, navigate]);
  return (
    <div className="p-10">
      <p role={error ? 'alert' : undefined}>{error || 'Completing secure sign-in…'}</p>
      <Link to="/auth/login">Return to sign-in</Link>
    </div>
  );
}
