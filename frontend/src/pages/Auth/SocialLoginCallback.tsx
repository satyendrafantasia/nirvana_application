import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Spinner from '../../components/common/Spinner';
import { useAuth } from '../../hooks/useAuth';
import { showToast } from '../../components/common/Toast';

const SocialLoginCallback = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { loginWithGoogleToken } = useAuth();

  useEffect(() => {
    const urlParams = new URLSearchParams(location.search);
    const token = urlParams.get('token');
    if (token) {
      loginWithGoogleToken(token)
        .then(() => {
          showToast('Logged in with Google', 'success');
          navigate('/');
        })
        .catch(() => {
          showToast('Google login failed', 'error');
          navigate('/auth/login');
        });
    } else {
      navigate('/auth/login');
    }
  }, [location.search, loginWithGoogleToken, navigate]);

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Spinner />
    </div>
  );
};

export default SocialLoginCallback;
