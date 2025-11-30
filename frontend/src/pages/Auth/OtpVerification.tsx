import { useMutation } from '@tanstack/react-query';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { requestOtp, verifyOtp } from '../../api/authApi';
import { useAuth } from '../../hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../../components/common/Toast';

const OtpVerification = () => {
  const navigate = useNavigate();
  const { setUser } = useAuth();

  const requestMutation = useMutation({
    mutationFn: (email: string) => requestOtp(email),
    onSuccess: () => showToast('OTP sent to your email', 'success')
  });

  const verifyMutation = useMutation({
    mutationFn: async (formData: FormData) => {
      const email = String(formData.get('email'));
      const code = String(formData.get('code'));
      const response = await verifyOtp(email, code);
      setUser(response.user);
    },
    onSuccess: () => {
      showToast('Verified successfully', 'success');
      navigate('/');
    },
    onError: () => showToast('Invalid code. Try again.', 'error')
  });

  const handleVerify = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    verifyMutation.mutate(new FormData(event.currentTarget));
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center px-4 py-10">
      <div className="card space-y-6 p-6">
        <h1 className="text-2xl font-bold text-slate-900">Verify with OTP</h1>
        <form className="space-y-4" onSubmit={handleVerify}>
          <Input name="email" type="email" label="Email" required />
          <div className="flex gap-2">
            <Input name="code" label="OTP" placeholder="123456" className="flex-1" required />
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                const emailInput = document.querySelector<HTMLInputElement>('input[name="email"]');
                if (emailInput?.value) requestMutation.mutate(emailInput.value);
              }}
            >
              Send OTP
            </Button>
          </div>
          <Button type="submit" className="w-full">
            {verifyMutation.isPending ? 'Verifying...' : 'Verify'}
          </Button>
        </form>
      </div>
    </div>
  );
};

export default OtpVerification;
