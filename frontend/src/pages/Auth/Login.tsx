import { useMutation } from '@tanstack/react-query';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { useAuth } from '../../hooks/useAuth';
import { showToast } from '../../components/common/Toast';

const Login = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirect = params.get('redirect') || '/';
  const { login } = useAuth();

  const mutation = useMutation({
    mutationFn: async (formData: FormData) => {
      const email = String(formData.get('email'));
      const password = String(formData.get('password'));
      await login({ email, password });
    },
    onSuccess: () => {
      showToast('Logged in successfully', 'success');
      navigate(redirect);
    },
    onError: () => showToast('Login failed. Check your credentials.', 'error')
  });

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    mutation.mutate(formData);
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center px-4 py-10">
      <div className="card space-y-6 p-6">
        <h1 className="text-2xl font-bold text-slate-900">Welcome back</h1>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <Input name="email" type="email" label="Email" placeholder="you@example.com" required />
          <Input name="password" type="password" label="Password" placeholder="••••••••" required />
          <Button type="submit" className="w-full">
            {mutation.isPending ? 'Signing in...' : 'Login'}
          </Button>
        </form>
        <p className="text-sm text-slate-600">
          New here? <Link to="/auth/register" className="text-primary-600">Create an account</Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
