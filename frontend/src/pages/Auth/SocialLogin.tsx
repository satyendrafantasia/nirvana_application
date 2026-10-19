import { useQuery } from '@tanstack/react-query';
import client from '../../api/client';
export default function SocialLogin() {
  const providers = useQuery({
    queryKey: ['social-providers'],
    queryFn: async () => (await client.get<string[]>('/api/auth/oauth2/providers')).data,
    retry: false
  });
  return (
    <div className="space-y-2">
      {providers.data?.map((provider) => (
        <a
          key={provider}
          className="block rounded-xl border p-3 text-center"
          href={`${import.meta.env.VITE_API_BASE_URL || ''}/api/auth/oauth2/authorize/${encodeURIComponent(provider)}`}
        >
          Continue with {provider}
        </a>
      ))}
      {providers.isSuccess && !providers.data.length && (
        <p className="text-sm text-slate-500">Social sign-in is not enabled by this spa yet.</p>
      )}
      {providers.isError && (
        <p className="text-sm text-slate-500">Social sign-in is temporarily unavailable.</p>
      )}
    </div>
  );
}
