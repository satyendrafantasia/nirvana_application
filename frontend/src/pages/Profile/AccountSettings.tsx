import { useAuth } from '../../hooks/useAuth';
export default function AccountSettings() {
  const { user } = useAuth();
  return (
    <div className="card mx-auto my-10 max-w-xl p-6">
      <h1 className="text-2xl font-bold">Your account</h1>
      <p>{user?.email}</p>
      <p>Account #{user?.id}</p>
    </div>
  );
}
