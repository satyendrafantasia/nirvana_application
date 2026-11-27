import { showToast } from '../../components/common/Toast';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { cachedEmail } from '../../api/emailLinks';
import { authView } from '../../api/authApi';
import client, { errorMessage } from '../../api/client';
import { returnPath } from '../../auth/returnPath';
import { useAuth } from '../../hooks/useAuth';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';

export default function EmailLinkCallback() {
  const [params] = useSearchParams();
  const flow = params.get('flow');
  const code = params.get('oobCode');
  const request = params.get('request') ?? '';
  const owner = flow === 'owner-register';
  const registration = owner || flow === 'register';
  const start = owner ? '/owner/signup' : registration ? '/auth/register' : '/auth/otp';
  const destination = returnPath(
    params.get('redirect'),
    owner ? '/owner/onboarding' : registration ? '/' : '/me/bookings',
    owner
  );
  const next = '?redirect=' + encodeURIComponent(destination);
  const [email, setEmail] = useState(() => cachedEmail(request));
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const valid = params.get('mode') === 'signIn' && !!code && (registration || flow === 'login');
  const finish = useMutation({
    mutationFn: async () => {
      const { data } = await client.post(
        '/api/auth/firebase/email-link/' + (registration ? 'register' : 'login'),
        { email: email.trim(), code }
      );
      try {
        localStorage.removeItem('nirvana-email-link:' + request);
      } catch {
        /* Storage is optional. */
      }
      if (registration) {
        navigate(start + next, {
          replace: true,
          state: { emailRegistrationToken: data.registrationToken }
        });
      } else if (data.mfaRequired) {
        sessionStorage.setItem('nirvana_mfa', data.mfaVerificationId);
        navigate('/auth/otp' + next, { replace: true });
      } else {
        acceptAuth(authView(data));
        showToast('You’re signed in.', 'success');
        navigate(destination, { replace: true });
      }
    }
  });
  return (
    <section className="card mx-auto my-10 max-w-md p-6 space-y-4">
      <h1 className="text-2xl font-bold">
        {registration ? 'Verify your email' : 'Sign in by email'}
      </h1>
      {valid ? (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            finish.mutate();
          }}
        >
          <Input
            label="Email that received this link"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Button type="submit" disabled={finish.isPending}>
            {registration ? 'Continue registration' : 'Sign in'}
          </Button>
          {finish.isError && <p role="alert">{errorMessage(finish.error)}</p>}
        </form>
      ) : (
        <p role="alert">
          This link is incomplete. Open the full link in your email or request another.
        </p>
      )}
      <Link className="underline" to={start + next}>
        Request a new email link
      </Link>
    </section>
  );
}
