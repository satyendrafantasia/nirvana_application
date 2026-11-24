import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CatalogPackage, PackageSpa, searchPackageSpas } from '../../api/packageApi';
import { errorMessage } from '../../api/client';
import { useDebounced } from '../../hooks/useDebounced';

export const packageName = (p: Pick<CatalogPackage, 'packageType'>) =>
  p.packageType.toLowerCase().replaceAll('_', ' ');

const SHOWN = 50;
const count = (n: number) => n.toLocaleString('en-IN');

function SpaLink({ spa }: { spa: Pick<PackageSpa, 'id' | 'name' | 'city'> }) {
  return (
    <>
      <Link className="underline" to={`/spas/${spa.id}`}>
        {spa.name}
      </Link>
      {spa.city && <span className="text-slate-500"> · {spa.city}</span>}
    </>
  );
}

/**
 * Spas matching a search, from the server: the 3,000+ spas that take packages are never all
 * downloaded. Requests wait until typing pauses; the last results stay up while new ones load.
 */
function useSpaSearch(query: string, packageId?: number, enabled = true) {
  const settled = useDebounced(query.trim(), 300);
  return useQuery({
    queryKey: ['package-spas', settled, packageId ?? null],
    queryFn: ({ signal }) => searchPackageSpas(settled, packageId, SHOWN, signal),
    enabled: enabled && (packageId !== undefined || settled.length >= 2),
    placeholderData: keepPreviousData,
    staleTime: 5 * 60_000
  });
}

/** The matches for a search, each with the packages that can be used there. */
export function SpaMatches({
  packages,
  total,
  spas,
  query
}: {
  packages: CatalogPackage[];
  total: number;
  spas: PackageSpa[];
  query: string;
}) {
  if (!total)
    return (
      <p className="text-slate-600">No spa matching “{query.trim()}” takes Nirvana packages yet.</p>
    );
  return (
    <>
      <p className="text-sm text-slate-500">
        {total === 1 ? '1 spa found' : `${count(total)} spas found`}
        {total > spas.length && `, showing the first ${spas.length}. Type more to narrow it down.`}
      </p>
      <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-200">
        {spas.map((s) => (
          <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
            <span>
              <SpaLink spa={s} />
            </span>
            <span className="flex flex-wrap gap-1">
              {packages.map((p) =>
                s.packageIds.includes(p.id) ? (
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
  );
}

/** "Is your spa included?": search spas by name or city and see which packages can be used there. */
export function PackageSpaSearch({ packages }: { packages: CatalogPackage[] }) {
  const [query, setQuery] = useState('');
  const found = useSpaSearch(query);
  const typed = query.trim().length >= 2;
  if (!packages.some((p) => p.eligibleSpaCount > 0)) return null;
  return (
    <section
      aria-labelledby="spa-search-title"
      className="space-y-4 rounded-3xl border border-slate-100 bg-white p-6 shadow-sm"
    >
      <div>
        <h2 id="spa-search-title" className="text-xl font-semibold">
          Is your spa included?
        </h2>
        <p className="text-slate-600">Search by spa name or city.</p>
      </div>
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="e.g. Fern House or Lucknow"
        aria-label="Search spas by name or city"
        className="w-full rounded-xl border border-slate-300 px-4 py-3"
      />
      <div aria-live="polite" aria-busy={found.isFetching} className="space-y-2">
        {typed && found.isError && <p role="alert">{errorMessage(found.error)}</p>}
        {typed && found.data && (
          <SpaMatches
            packages={packages}
            total={found.data.total}
            spas={found.data.spas}
            query={query}
          />
        )}
      </div>
    </section>
  );
}

/** Where a package can be used: a count, and a searchable list of spas on request. */
export function EligibleSpas({
  packageId,
  spaCount,
  initiallyOpen = false
}: {
  packageId: number;
  spaCount: number;
  initiallyOpen?: boolean;
}) {
  const [open, setOpen] = useState(initiallyOpen);
  const [query, setQuery] = useState('');
  const found = useSpaSearch(query, packageId, open);
  if (!spaCount) return <p className="text-slate-600">No eligible spas configured yet</p>;
  const listId = `package-${packageId}-spas`;
  const spas = found.data?.spas ?? [];
  const more = (found.data?.total ?? 0) - spas.length;
  return (
    <div className="space-y-2 text-slate-600">
      <p className="flex flex-wrap items-center justify-between gap-2">
        Available at {spaCount === 1 ? '1 spa' : `${count(spaCount)} spas`}
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
        <div id={listId} className="space-y-2" aria-busy={found.isFetching}>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search spa or city"
            aria-label="Search spas for this package"
            className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm"
          />
          {found.isError && <p role="alert">{errorMessage(found.error)}</p>}
          {found.isLoading && <p className="text-sm">Loading spas…</p>}
          {found.data && !spas.length && (
            <p className="text-sm">Not available at any spa matching “{query.trim()}”.</p>
          )}
          {spas.length > 0 && (
            <ul className="max-h-56 space-y-1 overflow-y-auto rounded-xl border border-slate-200 p-3 text-sm">
              {spas.map((s) => (
                <li key={s.id}>
                  <SpaLink spa={s} />
                </li>
              ))}
              {more > 0 && (
                <li className="pt-1 text-slate-500">
                  and {count(more)} more. Search to find a spa.
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
