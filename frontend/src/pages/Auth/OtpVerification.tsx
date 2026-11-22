import { returnPath } from '../../auth/returnPath';
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { requestOtp, verifyOtp } from '../../api/authApi';
import { errorMessage } from '../../api/client';
import { toE164, usePhoneCode, usePhoneCodeMode } from '../../api/phoneCodes';
import { useAuth } from '../../hooks/useAuth';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import ContactChoice, { ContactKind } from '../../components/common/ContactChoice';
export default function OtpVerification() {
  const mode = usePhoneCodeMode();
  const phone = usePhoneCode();
  const [kind, setKind] = useState<ContactKind>('email');
  const [contact, setContact] = useState('');
  const [otp, setOtp] = useState('');
  const [verificationId, setVerificationId] = useState(sessionStorage.getItem('nirvana_mfa') || '');
  const mfa = Boolean(sessionStorage.getItem('nirvana_mfa'));
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const request = useMutation({
    mutationFn: async () => {
      if (kind === 'phone') {
        const number = toE164(contact);
        if (!number) throw new Error('Enter a valid mobile number, for example 98765 43210.');
        const r = await requestOtp({ phoneNumber: number });
        await phone.start(number);
        return r;
      }
      phone.reset();
      return requestOtp({ email: contact.trim() });
    },
    onSuccess: (r) => {
      setOtp('');
      setVerificationId(r.verificationId);
    }
  });
  const verify = useMutation({
    mutationFn: async () => verifyOtp(verificationId, await phone.proof(otp)),
    onSuccess: (r) => {
      acceptAuth(r);
      navigate(returnPath(params.get('redirect'), '/me/bookings'));
    }
  });
  return (
    <form
      className="card mx-auto my-10 max-w-md p-6 space-y-5"
      onSubmit={(e) => {
        e.preventDefault();
        verify.mutate();
      }}
    >
      <h1 className="text-2xl font-bold">Sign in with a code</h1>
      {!mfa && (
        <>
          <ContactChoice
            mode={mode}
            kind={kind}
            onKind={(k) => {
              setKind(k);
              setVerificationId('');
            }}
            value={contact}
            onChange={setContact}
          />
          <Button
            type="button"
            disabled={!contact.trim() || request.isPending}
            onClick={() => request.mutate()}
          >
            {request.isPending ? 'Sending…' : verificationId ? 'Send a new code' : 'Send code'}
          </Button>
        </>
      )}
      <div ref={phone.slot} />
      {verificationId && (
        <>
          <p>
            {mfa
              ? 'Enter the code sent to your registered contact.'
              : `Enter the code sent to ${kind === 'phone' ? toE164(contact) : contact.trim()}.`}
          </p>
          <Input
            label="Six-digit code"
            value={otp}
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            required
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
          />
          <Button disabled={verify.isPending}>
            {verify.isPending ? 'Checking…' : 'Verify and sign in'}
          </Button>
        </>
      )}
      {(request.isError || verify.isError) && (
        <p role="alert">{errorMessage(request.error || verify.error)}</p>
      )}
    </form>
  );
}
