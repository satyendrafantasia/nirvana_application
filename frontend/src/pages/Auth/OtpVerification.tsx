import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { requestOtp, verifyOtp } from '../../api/authApi';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
export default function OtpVerification() {
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [verificationId, setVerificationId] = useState(sessionStorage.getItem('nirvana_mfa') || '');
  const { acceptAuth } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const request = useMutation({
    mutationFn: () => requestOtp(email),
    onSuccess: (r) => setVerificationId(r.verificationId)
  });
  const verify = useMutation({
    mutationFn: () => verifyOtp(verificationId, otp),
    onSuccess: (r) => {
      acceptAuth(r);
      navigate(params.get('redirect') === '/owner' ? '/owner' : '/me/bookings');
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
      <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <Button type="button" disabled={!email || request.isPending} onClick={() => request.mutate()}>
        Send code
      </Button>
      {verificationId && (
        <>
          <p>Enter the code sent to your registered contact.</p>
          <Input
            label="Six-digit code"
            value={otp}
            pattern="[0-9]{6}"
            maxLength={6}
            required
            onChange={(e) => setOtp(e.target.value)}
          />
          <Button disabled={verify.isPending}>Verify and sign in</Button>
        </>
      )}
      {(request.isError || verify.isError) && (
        <p role="alert">{errorMessage(request.error || verify.error)}</p>
      )}
    </form>
  );
}
