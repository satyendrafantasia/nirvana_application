import Button from '../common/Button';
import { Service } from '../../types/spa';

interface ServiceCardProps {
  service: Service;
  onBook?: (service: Service) => void;
}

const ServiceCard = ({ service, onBook }: ServiceCardProps) => {
  return (
    <div className="card flex items-center justify-between gap-4 p-4">
      <div>
        <h4 className="text-base font-semibold text-slate-800">{service.name}</h4>
        {service.description && <p className="text-sm text-slate-500">{service.description}</p>}
        <p className="text-sm text-slate-500">Duration: {service.durationMinutes} mins</p>
      </div>
      <div className="flex flex-col items-end gap-2">
        <p className="text-lg font-semibold text-primary-700">${service.price.toFixed(2)}</p>
        {onBook && (
          <Button size="sm" onClick={() => onBook(service)}>
            Book
          </Button>
        )}
      </div>
    </div>
  );
};

export default ServiceCard;
