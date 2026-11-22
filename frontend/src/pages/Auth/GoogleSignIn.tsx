import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useGoogleAuth } from '../../api/googleAuth';
import client, { errorMessage } from '../../api/client';
import { authView } from '../../api/authApi';
import { useAuth } from '../../hooks/useAuth';
import { returnPath } from '../../auth/returnPath';
import Button from '../../components/common/Button';

export default function GoogleSignIn({ register = false }: { register?: boolean }) {
  const google = useGoogleAuth();
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const destination = returnPath(params.get('redirect'), '/');
  const [consent, setConsent] = useState(false);
  const action = useMutation({
    mutationFn: async () => {
      const idToken = await google.data!();
      const { data } = await client.post('/api/auth/firebase/google', {
        idToken,
        createCustomer: register && consent
      });
      acceptAuth(authView(data));
      navigate(destination);
    },
    onError: () => {
      if (sessionStorage.getItem('nirvana_mfa'))
        navigate('/auth/otp?redirect=' + encodeURIComponent(destination));
    }
  });
  if (!google.data) return null;
  return (
    <div className="space-y-2">
      {register && (
        <label className="block text-sm">
          <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />{' '}
          I accept the{' '}
          <Link to="/privacy" className="underline">
            privacy policy
          </Link>{' '}
          to register with Google.
        </label>
      )}
      <Button
        type="button"
        variant="secondary"
        className="w-full"
        onClick={() => action.mutate()}
        disabled={action.isPending || (register && !consent)}
      >
        Continue with Google
      </Button>
      {action.isError && <p role="alert">{errorMessage(action.error)}</p>}
    </div>
  );
}
