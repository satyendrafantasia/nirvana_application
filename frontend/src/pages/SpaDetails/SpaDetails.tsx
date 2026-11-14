import { useEffect, useRef, useState } from 'react';
import { track } from '../../telemetry';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getSpaDetails, getSpaServices } from '../../api/spaApi';
import RatingBadge from '../../components/spa/RatingBadge';
import ServiceCard from '../../components/spa/ServiceCard';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { Service } from '../../types/spa';
import SpaInformation from '../../components/spa/SpaInformation';
import Spinner from '../../components/common/Spinner';

const SpaDetails = () => {
  const { spaId = '' } = useParams();
  const navigate = useNavigate();
  const { setSpa, setService } = useBookingFlow();

  const {
    data: spa,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['spa', spaId],
    queryFn: () => getSpaDetails(spaId),
    enabled: Boolean(spaId)
  });

  const { data: services, isLoading: loadingServices, isError: serviceError, refetch } = useQuery({
    queryKey: ['spa-services', spaId],
    queryFn: () => getSpaServices(spaId),
    enabled: Boolean(spaId)
  });

  const servicesSection = useRef<HTMLElement>(null);
  const [belowFold, setBelowFold] = useState(false);
  useEffect(() => {
    const section = servicesSection.current;
    if (!section || !services?.length) return;
    const observer = new IntersectionObserver(([entry]) => setBelowFold(entry.boundingClientRect.top >= window.innerHeight));
    observer.observe(section);
    return () => observer.disconnect();
  }, [spa, services]);
  const handleBook = (service: Service) => {
    if (!spa) return;
    setSpa(spa.id, spa.name);
    setService(service);
    track('select_service');
    navigate('/booking');
  };

  if (isError)
    return (
      <p role="alert" className="p-10">
        Unable to load this spa. Please retry from search.
      </p>
    );
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
            src={
              spa.coverImage ||
              'https://images.unsplash.com/photo-1556228724-4a44ebcdf3a8?auto=format&fit=crop&w=1200&q=80'
            }
            width={1200} height={720} alt={spa.coverImage ? spa.name : ''}
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
          {!!services?.length && <a className="font-semibold underline" href="#services">Choose a treatment</a>}
          <p className="text-slate-600">{spa.description}</p>
          <div className="rounded-2xl bg-primary-50 p-4 text-sm text-primary-800">
            <p className="font-semibold">Opening hours</p>
            <p>
              {spa.workingDays?.join(', ')} · {spa.openTimeLocal || 'Contact spa'} –{' '}
              {spa.closeTimeLocal || ''} {spa.timezone}
            </p>
          </div>
        </div>
      </div>

      <section ref={servicesSection} id="services" className="mt-10 space-y-4 scroll-mt-36">
        <h2 className="text-2xl font-semibold text-slate-900">Services</h2>
        {loadingServices && <p role="status">Loading treatments…</p>}
        {serviceError && <p role="alert">Treatments could not be loaded. <button className="underline" onClick={() => void refetch()}>Retry</button></p>}
        {services?.length === 0 && <p>No treatments are currently available to book.</p>}
        <div className="space-y-3">
          {services?.map((service) => (
            <ServiceCard key={service.id} service={service} onBook={handleBook} />
          ))}
        </div>
      </section>
      {belowFold && !!services?.length && <a href="#services" className="md:hidden fixed inset-x-4 z-20 rounded-xl bg-primary-700 px-4 py-3 text-center font-semibold text-white" style={{bottom:'max(1rem, env(safe-area-inset-bottom))'}}>Choose a treatment</a>}
      <SpaInformation spaId={spaId} />
    </div>
  );
};

export default SpaDetails;
