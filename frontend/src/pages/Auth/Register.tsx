import { showToast } from '../../components/common/Toast';
import { returnPath } from '../../auth/returnPath';
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { useAuth } from '../../hooks/useAuth';
import client, { errorMessage } from '../../api/client';
import { authView } from '../../api/authApi';
import { otpBody, toE164, usePhoneCode, usePhoneCodeMode } from '../../api/phoneCodes';
import { sendEmailLink } from '../../api/emailLinks';
import GoogleSignIn from './GoogleSignIn';
import ContactChoice, { ContactKind } from '../../components/common/ContactChoice';
export default function Register({ owner = false }: { owner?: boolean }) {
  const location = useLocation();
  const [params] = useSearchParams();
  const destination = returnPath(params.get('redirect'), owner ? '/owner/onboarding' : '/', owner);
  const mode = usePhoneCodeMode();
  const phone = usePhoneCode();
  const [kind, setKind] = useState<ContactKind>('email');
  const [email, setEmail] = useState(''),
    [verification, setVerification] = useState(''),
    [registration, setRegistration] = useState<string>(location.state?.emailRegistrationToken ?? '');
  const [sentTo, setSentTo] = useState('');
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const action = useMutation({
    mutationFn: async (form: FormData) => {
      if (!registration && !verification) {
        if (kind === 'phone') {
          const number = toE164(email);
          if (!number) throw new Error('Enter a valid mobile number, for example 98765 43210.');
          const { data } = await client.post('/api/auth/register/request-otp', { phoneNumber: number });
          await phone.start(number);
          setVerification(data.verificationId);
          return;
        }
        phone.reset();
        await sendEmailLink(email, owner ? 'owner-register' : 'register', destination);
        setSentTo(email.trim());
        return;
      }
      if (!registration) {
        const { data } = await client.post('/api/auth/register/verify-otp', {
          verificationId: verification,
          ...otpBody(await phone.proof(String(form.get('otp'))))
        });
        setRegistration(data.registrationToken);
        return;
      }
      if (form.get('password') !== form.get('confirm')) throw new Error('Passwords must match.');
      const { data } = await client.post('/api/auth/register/complete', {
        accountType: owner ? 'SPA_OWNER' : 'CUSTOMER',
        registrationToken: registration,
        acceptPrivacyPolicy: form.get('privacy') === 'on',
        fullName: String(form.get('fullName')),
        password: String(form.get('password')),
        whatsappUpdates: kind === 'phone' && form.get('whatsapp') === 'on'
      });
      acceptAuth(
        authView({
          ...data,
          userId: data.user.id,
          username: data.user.email || data.user.phoneNumber
        })
      );
      showToast('Your account is ready.', 'success');
      navigate(destination);
    }
  });
  return (
    <div className="max-w-md mx-auto p-8 space-y-5">
      <h1 className="text-2xl font-bold">
        {owner ? 'Create your spa owner account' : 'Create an account'}
      </h1>
      {owner && (
        <p>
          Create and manage your own spas. Each new spa stays unpublished until administrator
          approval.
        </p>
      )}
      <p>
        {registration
          ? 'Complete your verified account.'
          : verification
            ? `Enter the code sent to ${kind === 'phone' ? toE164(email) : email.trim()}.`
            : mode === 'FIREBASE' || mode === 'SMS'
              ? 'Verify your email or mobile number to create your account.'
              : 'Verify your email to create your account.'}
      </p>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          action.mutate(new FormData(e.currentTarget));
        }}
      >
        {!registration && !verification ? (
          <ContactChoice mode={mode} kind={kind} onKind={setKind} value={email} onChange={setEmail} />
        ) : !registration ? (
          <Input
            label="Verification code"
            name="otp"
            required
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            autoComplete="one-time-code"
          />
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
            <input name="privacy" type="checkbox" required /> I accept the <Link to="/privacy" className="underline" target="_blank">privacy policy</Link>.
          </label>
        )}
        {registration && kind === 'phone' && (
          <label className="block text-sm">
            <input name="whatsapp" type="checkbox" /> Send my booking confirmations and reminders on
            WhatsApp. Without an email address, this is how we can reach you about your bookings.
          </label>
        )}
        <Button disabled={action.isPending} type="submit">
          {action.isPending
            ? 'Please wait…'
            : registration
              ? 'Create account'
              : verification
                ? 'Verify code'
                : kind === 'email' ? 'Send registration link' : 'Send verification code'}
        </Button>
      </form>
      {sentTo && !registration && kind === 'email' && <p role="status">Open the registration link sent to {sentTo} to continue.</p>}
      {!owner && !registration && !verification && <GoogleSignIn register />}
      <div ref={phone.slot} />
      {verification && !registration && (
        <button
          className="text-primary-700 underline"
          onClick={() => {
            setVerification('');
            action.reset();
          }}
        >
          {kind === 'phone' ? 'Change number' : 'Change email'} or request another code
        </button>
      )}
      {action.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error)}
        </p>
      )}
      <Link to={(owner ? '/owner/login' : '/auth/login') + '?redirect=' + encodeURIComponent(destination)}>Already have an account? Sign in</Link>
    </div>
  );
}
