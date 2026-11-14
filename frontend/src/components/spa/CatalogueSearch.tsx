import Input from '../common/Input';
import Select from '../common/Select';
import Button from '../common/Button';

export default function CatalogueSearch({ params, onSearch, onNearby }: {
  params?: URLSearchParams;
  onSearch: (filters: URLSearchParams) => void;
  onNearby: () => void;
}) {
  return (
    <form className="space-y-4" onSubmit={(event) => {
      event.preventDefault();
      const filters = new URLSearchParams();
      new FormData(event.currentTarget).forEach((value, key) => {
        const text = String(value).trim();
        if (text) filters.set(key, text);
      });
      onSearch(filters);
    }}>
      <div className="grid gap-4 md:grid-cols-3">
        <Input label="City" name="city" placeholder="Gurgaon" defaultValue={params?.get('city') || ''} />
        <Input label="Spa name" name="spaName" placeholder="Enter a spa name" defaultValue={params?.get('spaName') || ''} />
        <Input label="Service" name="serviceName" placeholder="Massage, facial…" defaultValue={params?.get('serviceName') || params?.get('q') || ''} />
      </div>
      <div className="grid gap-4 items-end sm:grid-cols-2 lg:grid-cols-4">
        <Select label="Minimum rating" name="minRating" defaultValue={params?.get('minRating') || ''}>
          <option value="">Any rating</option>
          <option value="3">3 stars and up</option>
          <option value="4">4 stars and up</option>
          <option value="4.5">4.5 stars and up</option>
        </Select>
        <Select label="Sort by" name="sort" defaultValue={params?.get('sort') || ''}>
          <option value="">Recommended</option><option value="rating_desc">Highest rated</option><option value="name_asc">Name</option>
        </Select>
        <Button type="submit">Search spas</Button>
        <Button type="button" variant="secondary" onClick={onNearby}>Search nearby · Google</Button>
      </div>
    </form>
  );
}
