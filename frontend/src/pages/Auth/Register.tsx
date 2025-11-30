import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { useAuth } from '../../hooks/useAuth';
import { showToast } from '../../components/common/Toast';

const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();

  const mutation = useMutation({
    mutationFn: async (formData: FormData) => {
      const firstName = String(formData.get('firstName'));
      const lastName = String(formData.get('lastName'));
      const email = String(formData.get('email'));
      const password = String(formData.get('password'));
      await register({ firstName, lastName, email, password });
    },
    onSuccess: () => {
      showToast('Account created', 'success');
      navigate('/');
    },
    onError: () => showToast('Registration failed. Try again.', 'error')
  });

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    mutation.mutate(formData);
  };

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center px-4 py-10">
      <div className="card space-y-6 p-6">
        <h1 className="text-2xl font-bold text-slate-900">Create an account</h1>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="grid gap-3 md:grid-cols-2">
            <Input name="firstName" label="First name" required />
            <Input name="lastName" label="Last name" required />
          </div>
          <Input name="email" type="email" label="Email" required />
          <Input name="password" type="password" label="Password" required />
          <Button type="submit" className="w-full">
            {mutation.isPending ? 'Creating...' : 'Register'}
          </Button>
        </form>
        <p className="text-sm text-slate-600">
          Already have an account? <Link to="/auth/login" className="text-primary-600">Login</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
