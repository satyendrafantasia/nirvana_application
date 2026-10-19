import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { myPackages, resumePackage, payForPackage, packagePrice } from '../../api/packageApi';
import { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
export default function MyPackages() {
  const qc = useQueryClient();
  const owned = useQuery({ queryKey: ['my-packages'], queryFn: myPackages });
  const retry = useMutation({
    mutationFn: async (id: number) => payForPackage(await resumePackage(id)),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: ['my-packages'] });
    }
  });
  return (
    <div className="mx-auto max-w-5xl p-6 space-y-6">
      <div className="flex flex-wrap justify-between gap-4">
        <h1 className="text-3xl font-bold">My Packages</h1>
        <Button as={Link} to="/packages">
          Browse packages
        </Button>
      </div>
      <p className="text-slate-600">
        Your sessions and purchase status. Select “Use an eligible package I already own” at booking
        checkout to redeem a session.
      </p>
      {owned.isLoading && <p>Loading your packages…</p>}
      {(owned.isError || retry.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(owned.error || retry.error)}
        </p>
      )}
      {owned.data?.length === 0 && (
        <div className="rounded-2xl bg-white p-8">
          <h2 className="text-xl font-semibold">Your next wellness ritual starts here</h2>
          <p className="mt-2">You haven't purchased a package yet.</p>
          <Link to="/packages" className="text-primary-700 underline">
            Explore packages
          </Link>
        </div>
      )}
      {owned.data?.map((p) => (
        <article
          key={p.subscriptionId}
          className="rounded-2xl bg-white border border-slate-100 p-6 space-y-3"
        >
          <h2 className="text-xl font-semibold capitalize">
            {p.packageType.toLowerCase().replaceAll('_', ' ')} package
          </h2>
          <p>
            {p.status.replaceAll('_', ' ')} · Purchase #{p.subscriptionId}
          </p>
          <p className="text-2xl font-bold">{p.remainingSessions} sessions remaining</p>
          <p>
            {p.sessions} sessions included
            {p.currency && p.amountCents != null
              ? ` · ${packagePrice(p.amountCents, p.currency)}`
              : ''}
          </p>
          <p>
            {p.expiryDate
              ? `Expires ${new Date(p.expiryDate).toLocaleDateString()}`
              : p.status === 'PAYMENT_PENDING'
                ? 'Validity begins after payment is verified.'
                : 'No expiry date'}
          </p>
          {p.status === 'PAYMENT_PENDING' && (
            <Button disabled={retry.isPending} onClick={() => retry.mutate(p.subscriptionId)}>
              {retry.isPending ? 'Checking payment…' : 'Resume purchase'}
            </Button>
          )}
          {p.status === 'ACTIVE' && (
            <Button as={Link} to="/spas">
              Book a spa visit
            </Button>
          )}
        </article>
      ))}
    </div>
  );
}
