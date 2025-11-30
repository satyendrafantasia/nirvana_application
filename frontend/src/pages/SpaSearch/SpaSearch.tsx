import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { searchSpas } from '../../api/spaApi';
import SpaCard from '../../components/spa/SpaCard';
import Input from '../../components/common/Input';
import Select from '../../components/common/Select';
import Button from '../../components/common/Button';
import { Spa } from '../../types/spa';

const SpaSearch = () => {
  const [params, setParams] = useSearchParams();
  const queryParams = Object.fromEntries(params.entries());

  const { data: spas, isLoading } = useQuery({
    queryKey: ['spas', queryParams],
    queryFn: () => searchSpas(queryParams)
  });

  const handleFilter = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const nextParams = new URLSearchParams();
    formData.forEach((value, key) => {
      if (value) nextParams.set(key, String(value));
    });
    setParams(nextParams);
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-3xl font-bold text-slate-900">Find your next spa</h1>
      <p className="text-slate-600">Search by service, city, rating, or distance.</p>

      <form className="mt-6 grid gap-4 rounded-2xl bg-white p-4 shadow-card md:grid-cols-4" onSubmit={handleFilter}>
        <Input name="q" placeholder="Massage" defaultValue={params.get('q') || ''} label="Service" />
        <Input name="city" placeholder="Los Angeles" defaultValue={params.get('city') || ''} label="City" />
        <Select name="sort" defaultValue={params.get('sort') || ''} label="Sort by">
          <option value="">Recommended</option>
          <option value="rating">Rating</option>
          <option value="distance">Distance</option>
          <option value="price">Price</option>
        </Select>
        <div className="flex items-end">
          <Button type="submit" className="w-full">
            Search
          </Button>
        </div>
      </form>

      <div className="mt-8 grid gap-6 md:grid-cols-3">
        {isLoading && <p className="text-sm text-slate-500">Loading spas...</p>}
        {!isLoading && spas?.length === 0 && <p className="text-sm text-slate-500">No spas found.</p>}
        {spas?.map((spa: Spa) => (
          <SpaCard spa={spa} key={spa.id} />
        ))}
      </div>
    </div>
  );
};

export default SpaSearch;
