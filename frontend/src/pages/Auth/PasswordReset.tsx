import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { otpBody, toE164, usePhoneCode, usePhoneCodeMode } from '../../api/phoneCodes';
import ContactChoice, { ContactKind } from '../../components/common/ContactChoice';
export default function PasswordReset() {
  const mode = usePhoneCodeMode();
  const phone = usePhoneCode();
  const [kind, setKind] = useState<ContactKind>('email');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [verificationId, setVerificationId] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [done, setDone] = useState(false);
  const action = useMutation({
    mutationFn: async () => {
      if (!verificationId) {
        if (kind === 'phone') {
          const number = toE164(email);
          if (!number) throw new Error('Enter a valid mobile number, for example 98765 43210.');
          const { data } = await client.post('/api/auth/password/request-otp', {
            phoneNumber: number
          });
          await phone.start(number);
          setVerificationId(data.verificationId);
        } else {
          phone.reset();
          const { data } = await client.post('/api/auth/password/request-otp', {
            email: email.trim()
          });
          setVerificationId(data.verificationId);
        }
      } else if (!resetToken) {
        const { data } = await client.post('/api/auth/password/verify-otp', {
          verificationId,
          ...otpBody(await phone.proof(otp))
        });
        setResetToken(data.passwordResetToken);
      } else {
        if (password !== confirmation) throw new Error('Passwords do not match.');
        await client.post('/api/auth/password/reset', {
          passwordResetToken: resetToken,
          newPassword: password
        });
        setPassword('');
        setConfirmation('');
        setResetToken('');
        setDone(true);
      }
    }
  });
  return (
    <div className="max-w-md mx-auto p-8 my-8 bg-white rounded-2xl space-y-5">
      <h1 className="text-2xl font-bold">Reset your password</h1>
      {done ? (
        <p role="status">
          Password updated.{' '}
          <Link className="text-primary-700 underline" to="/auth/login">
            Sign in
          </Link>
        </p>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            action.mutate();
          }}
        >
          {!verificationId ? (
            <ContactChoice
              mode={mode}
              kind={kind}
              onKind={setKind}
              value={email}
              onChange={setEmail}
              emailLabel="Account email"
            />
          ) : !resetToken ? (
            <label className="block">
              Verification code
              <input
                className="block w-full border rounded-lg p-3"
                required
                pattern="[0-9]{6}"
                inputMode="numeric"
                maxLength={6}
                autoComplete="one-time-code"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              />
            </label>
          ) : (
            <>
              <label className="block">
                New password
                <input
                  className="block w-full border rounded-lg p-3"
                  required
                  minLength={8}
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
              <label className="block">
                Confirm password
                <input
                  className="block w-full border rounded-lg p-3"
                  required
                  minLength={8}
                  type="password"
                  autoComplete="new-password"
                  value={confirmation}
                  onChange={(e) => setConfirmation(e.target.value)}
                />
              </label>
            </>
          )}
          {action.isError && (
            <p role="alert" className="text-rose-700">
              {errorMessage(action.error)}
            </p>
          )}
          <Button disabled={action.isPending} type="submit">
            {action.isPending
              ? 'Please wait…'
              : !verificationId
                ? 'Send verification code'
                : !resetToken
                  ? 'Verify code'
                  : 'Save new password'}
          </Button>
        </form>
      )}
      <div ref={phone.slot} />
    </div>
  );
}
