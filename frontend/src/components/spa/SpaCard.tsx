import { Link } from 'react-router-dom';
import { Spa } from '../../types/spa';
import RatingBadge from './RatingBadge';
import LocationChip from './LocationChip';
import Button from '../common/Button';

interface SpaCardProps {
  spa: Spa;
}

const SpaCard = ({ spa }: SpaCardProps) => {
  return (
    <div className="card flex flex-col overflow-hidden">
      <div className="h-40 w-full overflow-hidden">
        <img
          src={spa.coverImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80'}
          alt={spa.name}
          className="h-full w-full object-cover"
        />
      </div>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-slate-800">{spa.name}</h3>
            <p className="text-sm text-slate-500">{spa.address}</p>
          </div>
          {spa.rating && <RatingBadge rating={spa.rating} count={spa.reviewCount} />}
        </div>
        <div className="flex flex-wrap gap-2 text-xs text-slate-600">
          {spa.categories?.map((category) => (
            <span key={category} className="rounded-full bg-primary-50 px-3 py-1 text-primary-700">
              {category}
            </span>
          ))}
          {spa.distanceKm && <LocationChip distanceKm={spa.distanceKm} city={spa.city} />}
        </div>
        <div className="mt-auto flex items-center justify-between">
          <p className="text-sm text-slate-500">Explore treatments & availability</p>
          <Button as={Link} to={`/spas/${spa.id}`} size="sm">
            View details
          </Button>
        </div>
      </div>
    </div>
  );
};

export default SpaCard;
