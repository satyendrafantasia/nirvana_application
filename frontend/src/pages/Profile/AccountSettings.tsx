import { useMutation, useQuery } from '@tanstack/react-query';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { getMe, updateProfile } from '../../api/userApi';
import { showToast } from '../../components/common/Toast';

const AccountSettings = () => {
  const { data: profile } = useQuery({ queryKey: ['me'], queryFn: getMe });

  const mutation = useMutation({
    mutationFn: async (formData: FormData) => {
      const payload = {
        firstName: String(formData.get('firstName')),
        lastName: String(formData.get('lastName')),
        phone: String(formData.get('phone') || ''),
        preferredCity: String(formData.get('preferredCity') || '')
      };
      await updateProfile(payload);
    },
    onSuccess: () => showToast('Profile updated', 'success'),
    onError: () => showToast('Unable to update profile', 'error')
  });

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    mutation.mutate(new FormData(event.currentTarget));
  };

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-3xl font-bold text-slate-900">Account settings</h1>
      <p className="text-slate-600">Update your personal information.</p>

      <form className="mt-6 space-y-4 rounded-2xl bg-white p-6 shadow-card" onSubmit={handleSubmit}>
        <div className="grid gap-3 md:grid-cols-2">
          <Input name="firstName" label="First name" defaultValue={profile?.firstName} required />
          <Input name="lastName" label="Last name" defaultValue={profile?.lastName} required />
        </div>
        <Input name="phone" label="Phone" defaultValue={profile?.phone} />
        <Input name="preferredCity" label="Preferred city" defaultValue={profile?.preferredCity} />
        <Button type="submit">{mutation.isPending ? 'Saving...' : 'Save changes'}</Button>
      </form>
    </div>
  );
};

export default AccountSettings;
