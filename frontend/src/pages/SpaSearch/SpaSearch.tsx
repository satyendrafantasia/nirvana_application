import { useState } from 'react';
import CatalogueSearch from '../../components/spa/CatalogueSearch';
import Discovery from '../Discovery/Discovery';
import SearchSource, { type SpaSource } from '../../components/spa/SearchSource';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { searchSpas } from '../../api/spaApi';
import SpaCard from '../../components/spa/SpaCard';
import { Spa } from '../../types/spa';

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

  const {
    data: spas,
    isLoading,
    isError
  } = useQuery({
    enabled: source === 'nirvana',
    queryKey: ['spas', queryParams],
    queryFn: () => searchSpas(queryParams)
  });

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

      {source === 'nirvana' && <div className="mt-8 grid gap-6 md:grid-cols-3">
        {isError && <p role="alert">Unable to load spas. Check the connection and retry.</p>}
        {isLoading && <p className="text-sm text-slate-500">Loading spas...</p>}
        {!isLoading && spas?.length === 0 && (
          <p className="text-sm text-slate-500">No spas found.</p>
        )}
        {spas?.map((spa: Spa) => (
          <SpaCard spa={spa} key={spa.id} />
        ))}
      </div>}
    </div>
  );
};

export default SpaSearch;
