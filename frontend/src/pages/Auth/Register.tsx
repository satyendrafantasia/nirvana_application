import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { useAuth } from '../../hooks/useAuth';
import client, { errorMessage } from '../../api/client';
import { authView } from '../../api/authApi';
export default function Register() {
  const [email, setEmail] = useState(''),
    [verification, setVerification] = useState(''),
    [registration, setRegistration] = useState('');
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const action = useMutation({
    mutationFn: async (form: FormData) => {
      if (!verification) {
        const { data } = await client.post('/api/auth/register/request-otp', { email });
        setVerification(data.verificationId);
        return;
      }
      if (!registration) {
        const { data } = await client.post('/api/auth/register/verify-otp', {
          verificationId: verification,
          otp: String(form.get('otp'))
        });
        setRegistration(data.registrationToken);
        return;
      }
      if (form.get('password') !== form.get('confirm')) throw new Error('Passwords must match.');
      const { data } = await client.post('/api/auth/register/complete', {
        registrationToken: registration,
        acceptPrivacyPolicy: form.get('privacy') === 'on',
        fullName: String(form.get('fullName')),
        password: String(form.get('password'))
      });
      acceptAuth(
        authView({
          ...data,
          userId: data.user.id,
          username: data.user.email || data.user.phoneNumber
        })
      );
      navigate('/');
    }
  });
  return (
    <div className="max-w-md mx-auto p-8 space-y-5">
      <h1 className="text-2xl font-bold">Create an account</h1>
      <p>
        {registration
          ? 'Complete your verified account.'
          : verification
            ? `Enter the code sent to ${email}.`
            : 'Verify your email to create your account.'}
      </p>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          action.mutate(new FormData(e.currentTarget));
        }}
      >
        {!verification ? (
          <Input
            label="Email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        ) : !registration ? (
          <Input label="Verification code" name="otp" required autoComplete="one-time-code" />
        ) : (
          <>
            <Input label="Full name" name="fullName" required minLength={2} />
            <Input
              label="Password"
              name="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={255}
            />
            <Input label="Confirm password" name="confirm" type="password" required minLength={8} />
          </>
        )}
        {registration && (
          <label className="block text-sm">
            <input name="privacy" type="checkbox" required /> I accept the spa platform privacy
            policy.
          </label>
        )}
        <Button disabled={action.isPending} type="submit">
          {action.isPending
            ? 'Please wait…'
            : registration
              ? 'Create account'
              : verification
                ? 'Verify code'
                : 'Send verification code'}
        </Button>
      </form>
      {verification && !registration && (
        <button
          className="text-primary-700 underline"
          onClick={() => {
            setVerification('');
            action.reset();
          }}
        >
          Change email or request another code
        </button>
      )}
      {action.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error)}
        </p>
      )}
      <Link to="/auth/login">Already have an account? Sign in</Link>
    </div>
  );
}
