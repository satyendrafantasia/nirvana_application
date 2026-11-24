import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CatalogPackage,
  listPackages,
  startPackage,
  payForPackage,
  packagePrice
} from '../../api/packageApi';
import { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { EligibleSpas, PackageSpaSearch, packageName } from './PackageSpaSearch';
export { EligibleSpas, PackageSpaSearch, SpaMatches } from './PackageSpaSearch';

export default function Packages() {
  const catalog = useQuery({
    queryKey: ['packages'],
    queryFn: () => listPackages(),
    staleTime: 5 * 60_000
  });
  const [selected, setSelected] = useState<CatalogPackage>();
  const [success, setSuccess] = useState<{ id: number; message: string }>();
  const feedback = useRef<HTMLElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    if (selected || success) {
      feedback.current?.focus({ preventScroll: true });
      feedback.current?.scrollIntoView({ block: 'center' });
    }
  }, [selected, success]);
  const qc = useQueryClient();
  const buy = useMutation({
    mutationFn: async (p: CatalogPackage) => payForPackage(await startPackage(p.id)),
    onSuccess: (p, item) => {
      setSelected(undefined);
      setSuccess({
        id: item.id,
        message: `Your ${p.packageType} package is ${p.status.toLowerCase()}.`
      });
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: ['my-packages'] });
    }
  });
  return (
    <div className="mx-auto max-w-6xl p-6 space-y-6">
      <div className="rounded-3xl bg-primary-50 p-8">
        <p className="text-primary-700 font-semibold">NIRVANA PACKAGES</p>
        <h1 className="text-3xl font-bold mt-2">Make wellness a regular ritual</h1>
        <p className="mt-3 text-slate-600">
          Choose a session package, pay securely, and redeem your visits when booking at eligible
          spas.
        </p>
        <Link className="inline-block mt-4 text-primary-700 underline" to="/me/packages">
          View My Packages →
        </Link>
      </div>
      {catalog.isLoading && <p>Loading packages…</p>}
      {catalog.isError && (
        <div role="alert">
          <p>{errorMessage(catalog.error)}</p>
          <Button onClick={() => void catalog.refetch()}>Retry</Button>
        </div>
      )}
      {catalog.data?.length === 0 && (
        <p>There are no packages for sale yet. Please check back soon.</p>
      )}
      {catalog.data && <PackageSpaSearch packages={catalog.data} />}
      <div className="grid gap-6 md:grid-cols-3">
        {catalog.data?.map((p) => (
          <article
            key={p.id}
            className="rounded-3xl bg-white p-6 shadow-sm border border-slate-100 flex flex-col gap-4"
          >
            <h2 className="text-2xl font-semibold capitalize">{packageName(p)} package</h2>
            <p className="text-3xl font-bold text-primary-700">
              {packagePrice(p.amountCents, p.currency)}
            </p>
            <p>{p.description}</p>
            <ul className="space-y-2 text-slate-600">
              <li>{p.sessions} spa sessions</li>
              <li>
                {p.validityDays
                  ? `Valid for ${p.validityDays} days from activation`
                  : 'No expiry date'}
              </li>
            </ul>
            <EligibleSpas packageId={p.id} spaCount={p.eligibleSpaCount} />
            <Button
              className="mt-auto"
              aria-expanded={selected?.id === p.id}
              disabled={buy.isPending || !p.eligibleSpaCount}
              onClick={(event: React.MouseEvent<HTMLButtonElement>) => {
                trigger.current = event.currentTarget;
                setSuccess(undefined);
                setSelected(p);
                buy.reset();
              }}
            >
              Buy package
            </Button>
            {selected?.id === p.id && (
              <section
                ref={feedback}
                tabIndex={-1}
                aria-label="Review package purchase"
                className="rounded-2xl border border-primary-200 bg-white p-6 space-y-4"
              >
                <h2 className="text-xl font-semibold">
                  Review your {selected.packageType.toLowerCase().replaceAll('_', ' ')} package
                </h2>
                <p>
                  {selected.sessions} sessions ·{' '}
                  {packagePrice(selected.amountCents, selected.currency)} · One-time purchase
                </p>
                <p className="text-slate-600">
                  Your sessions become available after payment is verified. You can hold one active
                  package at a time.
                </p>
                {buy.isError && (
                  <div role="alert" className="text-rose-700">
                    {errorMessage(buy.error)}{' '}
                    <Link to="/me/packages" className="underline">
                      My packages
                    </Link>
                  </div>
                )}
                <div className="flex flex-wrap gap-3">
                  <Button disabled={buy.isPending} onClick={() => buy.mutate(selected)}>
                    {buy.isPending ? 'Processing…' : 'Pay with Razorpay'}
                  </Button>
                  <Button
                    variant="ghost"
                    disabled={buy.isPending}
                    onClick={() => {
                      setSelected(undefined);
                      trigger.current?.focus();
                    }}
                  >
                    Back
                  </Button>
                </div>
              </section>
            )}
            {success?.id === p.id && (
              <section
                role="status"
                tabIndex={-1}
                ref={feedback}
                className="rounded-xl bg-emerald-50 p-4"
              >
                {success.message}{' '}
                <Link to="/me/packages" className="underline">
                  View your sessions
                </Link>
              </section>
            )}
          </article>
        ))}
      </div>
    </div>
  );
}
