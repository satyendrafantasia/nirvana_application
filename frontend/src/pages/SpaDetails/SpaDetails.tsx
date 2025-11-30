import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getSpaDetails, getSpaServices } from '../../api/spaApi';
import RatingBadge from '../../components/spa/RatingBadge';
import ServiceCard from '../../components/spa/ServiceCard';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { Service } from '../../types/spa';
import Spinner from '../../components/common/Spinner';

const SpaDetails = () => {
  const { spaId = '' } = useParams();
  const navigate = useNavigate();
  const { setSpa, setService } = useBookingFlow();

  const { data: spa, isLoading } = useQuery({
    queryKey: ['spa', spaId],
    queryFn: () => getSpaDetails(spaId),
    enabled: Boolean(spaId)
  });

  const { data: services } = useQuery({
    queryKey: ['spa-services', spaId],
    queryFn: () => getSpaServices(spaId),
    enabled: Boolean(spaId)
  });

  const handleBook = (service: Service) => {
    if (!spa) return;
    setSpa(spa.id, spa.name);
    setService(service);
    navigate('/booking');
  };

  if (isLoading || !spa) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="grid gap-6 rounded-3xl bg-white p-6 shadow-card md:grid-cols-5">
        <div className="md:col-span-3">
          <img
            src={spa.coverImage || 'https://images.unsplash.com/photo-1556228724-4a44ebcdf3a8?auto=format&fit=crop&w=1200&q=80'}
            alt={spa.name}
            className="h-80 w-full rounded-2xl object-cover"
          />
        </div>
        <div className="flex flex-col gap-4 md:col-span-2">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-3xl font-bold text-slate-900">{spa.name}</h1>
              <p className="text-sm text-slate-600">{spa.address}</p>
            </div>
            <RatingBadge rating={spa.rating} count={spa.reviewCount} />
          </div>
          <p className="text-slate-600">Luxurious treatments, serene spaces, and expert therapists.</p>
          <div className="rounded-2xl bg-primary-50 p-4 text-sm text-primary-800">
            <p className="font-semibold">Opening hours</p>
            <p>Mon - Sun · 9:00 AM - 9:00 PM</p>
          </div>
        </div>
      </div>

      <section className="mt-10 space-y-4">
        <h2 className="text-2xl font-semibold text-slate-900">Services</h2>
        <div className="space-y-3">
          {services?.map((service) => (
            <ServiceCard key={service.id} service={service} onBook={handleBook} />
          ))}
        </div>
      </section>
    </div>
  );
};

export default SpaDetails;
