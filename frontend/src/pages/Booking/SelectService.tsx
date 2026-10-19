import { useQuery } from '@tanstack/react-query';
import { searchSpas, getSpaServices } from '../../api/spaApi';
import SpaCard from '../../components/spa/SpaCard';
import ServiceCard from '../../components/spa/ServiceCard';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { Spa } from '../../types/spa';
import Spinner from '../../components/common/Spinner';

interface SelectServiceProps {
  onNext: () => void;
}

const SelectService = ({ onNext }: SelectServiceProps) => {
  const { state, setSpa, setService } = useBookingFlow();

  const {
    data: spas,
    isLoading: loadingSpas,
    isError: spasError
  } = useQuery({
    queryKey: ['booking-spas'],
    queryFn: () => searchSpas({ size: 6 })
  });

  const {
    data: services,
    isLoading: loadingServices,
    isError: servicesError
  } = useQuery({
    queryKey: ['booking-services', state.spaId],
    queryFn: () => getSpaServices(state.spaId || ''),
    enabled: Boolean(state.spaId)
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-semibold text-slate-900">Choose spa & service</h2>
        {state.service && (
          <Button onClick={onNext} size="sm">
            Continue
          </Button>
        )}
      </div>
      <p className="text-sm text-slate-600">Pick your spa and service to get started.</p>

      <div className="grid gap-4 md:grid-cols-3">
        {spasError && <p role="alert">Unable to load spas. Please retry.</p>}
        {loadingSpas && <Spinner />}
        {spas?.map((spa: Spa) => (
          <div key={spa.id} onClick={() => setSpa(spa.id, spa.name)} className="cursor-pointer">
            <SpaCard spa={spa} />
          </div>
        ))}
      </div>

      {state.spaId && (
        <div className="space-y-3">
          <h3 className="text-xl font-semibold text-slate-900">Services at {state.spaName}</h3>
          {servicesError && <p role="alert">Unable to load services.</p>}
          {services?.length === 0 && <p>No bookable services at this spa.</p>}
          {loadingServices && <Spinner />}
          {services?.map((service) => (
            <ServiceCard key={service.id} service={service} onBook={(svc) => setService(svc)} />
          ))}
        </div>
      )}
    </div>
  );
};

export default SelectService;
