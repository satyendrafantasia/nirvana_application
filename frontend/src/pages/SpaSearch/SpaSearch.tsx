import { useState } from 'react';
import CatalogueSearch from '../../components/spa/CatalogueSearch';
import Discovery from '../Discovery/Discovery';
import SearchSource, { type SpaSource } from '../../components/spa/SearchSource';
import { useSearchParams } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { searchSpasPage } from '../../api/spaApi';
import SpaCard from '../../components/spa/SpaCard';
import Button from '../../components/common/Button';
import { Spa } from '../../types/spa';

export const PAGE_SIZE = 24;

export function resultsSummary(shown: number, total: number) {
  return total === 1 ? '1 spa' : shown < total ? `Showing ${shown} of ${total} spas` : `${total} spas`;
}

const SpaSearch = () => {
  const [params, setParams] = useSearchParams();
  const [nearby, setNearby] = useState(false);
  const source = params.get('source') === 'google' ? 'google' : 'nirvana';
  const switchSource = (next: SpaSource) => {
    const filters = new URLSearchParams(params);
    if (next === 'google') filters.set('source', next);
    else filters.delete('source');
    setParams(filters);
  };
  const filters = new URLSearchParams(params);
  filters.delete('source');
  const queryParams = Object.fromEntries(filters);

  const results = useInfiniteQuery({
    enabled: source === 'nirvana',
    queryKey: ['spas', queryParams],
    queryFn: ({ pageParam }) => searchSpasPage({ ...queryParams, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined)
  });
  const spas = results.data?.pages.flatMap((p) => p.spas) ?? [];
  const total = results.data?.pages[0]?.total ?? 0;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="search-panel browse-search-panel">
        <div className="search-heading">
          <h1 className="text-3xl font-bold">Find your next spa</h1>
          <SearchSource value={source} onChange={(next) => { setNearby(false); switchSource(next); }} />
        </div>
        {source === 'google' ? <Discovery embedded startNearby={nearby} initialCity={params.get('city') || ''} initialSpaName={params.get('spaName') || ''} /> :
          <CatalogueSearch key={params.toString()} params={params} onSearch={setParams}
            onNearby={() => { setNearby(true); switchSource('google'); }} />}
      </div>

      {source === 'nirvana' && (
        <>
          {total > 0 && (
            <p role="status" className="mt-6 text-sm text-slate-600">{resultsSummary(spas.length, total)}</p>
          )}
          <div className="mt-4 grid gap-6 md:grid-cols-3">
            {results.isError && <p role="alert">Unable to load spas. Check the connection and retry.</p>}
            {results.isLoading && <p className="text-sm text-slate-500">Loading spas...</p>}
            {!results.isLoading && !results.isError && spas.length === 0 && (
              <p className="text-sm text-slate-500">No spas found.</p>
            )}
            {spas.map((spa: Spa) => (
              <SpaCard spa={spa} key={spa.id} />
            ))}
          </div>
          {results.hasNextPage && (
            <div className="mt-8 flex justify-center">
              <Button variant="secondary" disabled={results.isFetchingNextPage} onClick={() => void results.fetchNextPage()}>
                {results.isFetchingNextPage ? 'Loading more spas…' : 'Show more spas'}
              </Button>
            </div>
          )}
          {results.isFetchNextPageError && (
            <p role="alert" className="mt-4 text-center">Couldn't load more spas. Try again.</p>
          )}
        </>
      )}
    </div>
  );
};

export default SpaSearch;
