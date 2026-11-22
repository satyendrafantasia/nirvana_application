import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Account } from '../../api/accountApi';
import { requestOtp } from '../../api/authApi';
import client, { errorMessage } from '../../api/client';
import { useGoogleAuth } from '../../api/googleAuth';
import { otpBody, usePhoneCode } from '../../api/phoneCodes';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';

export default function ConnectGoogle({ account }: { account: Account }) {
  const google = useGoogleAuth();
  const qc = useQueryClient();
  const key = ['google-link', account.id];
  const status = useQuery({
    queryKey: key,
    queryFn: async () =>
      (await client.get<{ customer: boolean; email: string | null }>('/api/me/account/google')).data
  });
  const phone = usePhoneCode();
  const [challenge, setChallenge] = useState('');
  const [code, setCode] = useState('');
  const [proof, setProof] = useState('');
  const mobile = account.phoneVerified && account.phone;
  const email = account.emailVerified && account.email;
  const action = useMutation({
    mutationFn: async () => {
      if (proof) {
        const idToken = await google.data!();
        const { data } = await client.post('/api/me/account/google', {
          idToken,
          loginToken: proof
        });
        qc.setQueryData(key, data);
        setProof('');
      } else if (challenge) {
        const { data } = await client.post('/api/auth/login/otp/verify', {
          verificationId: challenge,
          ...otpBody(await phone.proof(code))
        });
        setProof(data.loginToken);
        setCode('');
      } else {
        if (!mobile && !email)
          throw new Error('Verify an account contact before connecting Google.');
        const result = await requestOtp(
          mobile ? { phoneNumber: mobile } : { email: String(email) }
        );
        if (mobile) await phone.start(mobile);
        setChallenge(result.verificationId);
      }
    }
  });
  if (!google.data || !status.data?.customer) return null;
  return (
    <section className="card space-y-3 p-6">
      <h2 className="text-xl font-semibold">Sign-in methods</h2>
      {status.data.email ? (
        <p>Google connected as {status.data.email}</p>
      ) : (
        <>
          <p>
            {proof
              ? 'Choose the Google account you want to use for Nirvana.'
              : challenge
                ? 'Enter the verification code sent to your account contact.'
                : 'Verify your existing account to use Google with your bookings and packages.'}
          </p>
          {challenge && !proof && (
            <Input
              label="Verification code"
              value={code}
              inputMode="numeric"
              autoComplete="one-time-code"
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            />
          )}
          <Button
            type="button"
            disabled={action.isPending || (!!challenge && !proof && code.length !== 6)}
            onClick={() => action.mutate()}
          >
            {proof ? 'Choose Google account' : challenge ? 'Verify code' : 'Connect Google'}
          </Button>
          {challenge && (
            <Button
              variant="ghost"
              disabled={action.isPending}
              onClick={() => {
                phone.reset();
                setChallenge('');
                setCode('');
                setProof('');
                action.reset();
              }}
            >
              Start over
            </Button>
          )}
          {action.isError && <p role="alert">{errorMessage(action.error)}</p>}
        </>
      )}
      <div ref={phone.slot} />
    </section>
  );
}
