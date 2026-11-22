import { useEffect, useMemo, useRef, useState } from 'react';
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
const packageName = (p: Pick<CatalogPackage, 'packageType'>) =>
  p.packageType.toLowerCase().replaceAll('_', ' ');
const fold = (text: string) =>
  text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
type EligibleSpa = CatalogPackage['eligibleSpas'][number];

/** Spas whose name or city contains every word typed; names that start with the search come first. */
export function matchSpas<T extends EligibleSpa>(query: string, spas: T[]): T[] {
  const words = fold(query).split(' ').filter(Boolean);
  if (!words.length) return [...spas].sort((a, b) => a.name.localeCompare(b.name));
  const q = words.join(' ');
  return spas
    .map((s) => ({ s, name: fold(s.name), all: fold(`${s.name} ${s.city ?? ''}`) }))
    .filter((x) => words.every((w) => x.all.includes(w)))
    .sort(
      (a, b) =>
        Number(b.name.startsWith(q)) - Number(a.name.startsWith(q)) ||
        Number(b.name.includes(q)) - Number(a.name.includes(q)) ||
        a.s.name.localeCompare(b.s.name)
    )
    .map((x) => x.s);
}

const SHOWN = 50;
const count = (n: number) => n.toLocaleString('en-IN');

function SpaLink({ spa }: { spa: EligibleSpa }) {
  return (
    <>
      <Link className="underline" to={`/spas/${spa.id}`}>
        {spa.name}
      </Link>
      {spa.city && <span className="text-slate-500"> · {spa.city}</span>}
    </>
  );
}

/** "Is your spa included?": search every spa once and see which packages can be used there. */
export function PackageSpaSearch({
  packages,
  initialQuery = ''
}: {
  packages: CatalogPackage[];
  initialQuery?: string;
}) {
  const [query, setQuery] = useState(initialQuery);
  const spas = useMemo(() => {
    const byId = new Map<number, EligibleSpa & { packages: Set<number> }>();
    for (const p of packages)
      for (const s of p.eligibleSpas) {
        const spa = byId.get(s.id) ?? { ...s, packages: new Set<number>() };
        spa.packages.add(p.id);
        byId.set(s.id, spa);
      }
    return [...byId.values()];
  }, [packages]);
  const typed = fold(query).length >= 2;
  const found = useMemo(() => (typed ? matchSpas(query, spas) : []), [typed, query, spas]);
  if (!spas.length) return null;
  return (
    <section
      aria-labelledby="spa-search-title"
      className="space-y-4 rounded-3xl border border-slate-100 bg-white p-6 shadow-sm"
    >
      <div>
        <h2 id="spa-search-title" className="text-xl font-semibold">
          Is your spa included?
        </h2>
        <p className="text-slate-600">
          Packages can be used at {count(spas.length)} spas. Search by spa name or city.
        </p>
      </div>
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="e.g. Fern House or Lucknow"
        aria-label="Search spas by name or city"
        className="w-full rounded-xl border border-slate-300 px-4 py-3"
      />
      <div aria-live="polite" className="space-y-2">
        {typed && found.length === 0 && (
          <p className="text-slate-600">
            No spa matching “{query.trim()}” takes Nirvana packages yet.
          </p>
        )}
        {found.length > 0 && (
          <>
            <p className="text-sm text-slate-500">
              {found.length === 1 ? '1 spa found' : `${count(found.length)} spas found`}
              {found.length > SHOWN && `, showing the first ${SHOWN}. Type more to narrow it down.`}
            </p>
            <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-200">
              {found.slice(0, SHOWN).map((s) => (
                <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
                  <span>
                    <SpaLink spa={s} />
                  </span>
                  <span className="flex flex-wrap gap-1">
                    {packages.map((p) =>
                      s.packages.has(p.id) ? (
                        <span
                          key={p.id}
                          className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-semibold capitalize text-emerald-800"
                        >
                          ✓ {packageName(p)}
                        </span>
                      ) : (
                        <span
                          key={p.id}
                          className="rounded-full bg-slate-100 px-2 py-0.5 text-xs capitalize text-slate-500 line-through"
                        >
                          <span className="sr-only">Not available: </span>
                          {packageName(p)}
                        </span>
                      )
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  );
}

/** Where a package can be used: a count and a button, with a searchable list shown on request. */
export function EligibleSpas({
  packageId,
  spas,
  initiallyOpen = false
}: {
  packageId: number;
  spas: CatalogPackage['eligibleSpas'];
  initiallyOpen?: boolean;
}) {
  const [open, setOpen] = useState(initiallyOpen);
  const [query, setQuery] = useState('');
  const shown = useMemo(() => (open ? matchSpas(query, spas) : []), [open, query, spas]);
  if (!spas.length) return <p className="text-slate-600">No eligible spas configured yet</p>;
  if (spas.length === 1)
    return (
      <p className="text-slate-600">
        Available at <SpaLink spa={spas[0]} />
      </p>
    );
  const listId = `package-${packageId}-spas`;
  return (
    <div className="space-y-2 text-slate-600">
      <p className="flex flex-wrap items-center justify-between gap-2">
        Available at {count(spas.length)} spas
        <button
          type="button"
          className="font-semibold text-primary-700 underline"
          aria-expanded={open}
          aria-controls={listId}
          onClick={() => setOpen(!open)}
        >
          {open ? 'Hide spas' : 'View spas'}
        </button>
      </p>
      {open && (
        <div id={listId} className="space-y-2">
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search spa or city"
            aria-label="Search spas for this package"
            className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
          />
          {shown.length === 0 ? (
            <p className="text-sm">Not available at any spa matching “{query.trim()}”.</p>
          ) : (
            <ul className="max-h-56 space-y-1 overflow-y-auto rounded-xl border border-slate-200 p-3 text-sm">
              {shown.slice(0, SHOWN).map((s) => (
                <li key={s.id}>
                  <SpaLink spa={s} />
                </li>
              ))}
              {shown.length > SHOWN && (
                <li className="pt-1 text-slate-500">
                  and {count(shown.length - SHOWN)} more. Search to find a spa.
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export default function Packages() {
  const catalog = useQuery({ queryKey: ['packages'], queryFn: listPackages });
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
            <EligibleSpas packageId={p.id} spas={p.eligibleSpas} />
            <Button
              className="mt-auto"
              aria-expanded={selected?.id === p.id}
              disabled={buy.isPending || !p.eligibleSpas.length}
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
