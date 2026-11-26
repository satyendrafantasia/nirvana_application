import { Link } from 'react-router-dom';
import { Spa } from '../../types/spa';
import RatingBadge from './RatingBadge';
import LocationChip from './LocationChip';
import Button from '../common/Button';
import { responsiveImage } from '../../api/responsiveImage';

interface SpaCardProps {
  spa: Spa;
}

export function SpaCardLayout({ cover, name, address, rating, count, children }: {
  cover: React.ReactNode;
  name: string;
  address?: string;
  rating?: number;
  count?: number;
  children: React.ReactNode;
}) {
  return (
    <article className="card spa-card flex min-w-0 flex-col overflow-hidden">
      <div className="relative h-52 w-full overflow-hidden">{cover}</div>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-slate-800">{name}</h3>
            <p className="text-sm text-slate-500">{address}</p>
          </div>
          {rating != null && <RatingBadge rating={rating} count={count} />}
        </div>
        {children}
      </div>
    </article>
  );
}

const SpaCard = ({ spa }: SpaCardProps) => {
  return (
    // No reviews yet: show no stars rather than "0.0".
    <SpaCardLayout name={spa.name} address={spa.address} rating={spa.reviewCount ? spa.rating : undefined} count={spa.reviewCount}
      cover={<img
          {...responsiveImage(
            spa.coverImage ||
              'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80'
          )}
          sizes="(max-width: 639px) 100vw, (max-width: 1023px) 50vw, 400px"
          loading="lazy" decoding="async" width={800} height={480} alt={spa.coverImage ? spa.name : ''}
          className="h-full w-full object-cover"
        />}>
        <div className="flex flex-wrap gap-2 text-xs text-slate-600">
          {spa.categories?.map((category) => (
            <span key={category} className="rounded-full bg-primary-50 px-3 py-1 text-primary-700">
              {category}
            </span>
          ))}
          {spa.distanceKm && <LocationChip distanceKm={spa.distanceKm} city={spa.city} />}
        </div>
        <div className="mt-auto flex flex-wrap gap-2 items-center justify-between">
          <p className="text-sm text-slate-500">{spa.bookingMode==='ENQUIRY'?'Enquiry only':spa.bookingMode==='REQUEST'?'Request to book':'Instant booking'}</p>
          <Button as={Link} to={`/spas/${spa.id}`} size="sm">
            View details
          </Button>
        </div>
    </SpaCardLayout>
  );
};

export default SpaCard;
