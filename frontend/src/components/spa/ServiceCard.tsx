import { useEffect, useRef } from 'react';
import { track } from '../../telemetry';
import Button from '../common/Button';
import { Service } from '../../types/spa';

interface ServiceCardProps {
  service: Service;
  onBook?: (service: Service) => void;
}

const ServiceCard = ({ service, onBook }: ServiceCardProps) => {
  const card = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!card.current || !('IntersectionObserver' in window)) return;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { track('view_service'); observer.disconnect(); }
    });
    observer.observe(card.current);
    return () => observer.disconnect();
  }, [service.id]);
  return (
    <div ref={card} className="card flex flex-wrap items-center justify-between gap-4 p-4">
      <div>
        <h4 className="text-base font-semibold text-slate-800">{service.name}</h4>
        {service.description && <p className="text-sm text-slate-500">{service.description}</p>}
        <p className="text-sm text-slate-500">Duration: {service.durationMinutes} mins</p>
      </div>
      <div className="flex flex-col items-end gap-2">
        <p className="text-lg font-semibold text-primary-700">
          {new Intl.NumberFormat('en-IN', { style: 'currency', currency: service.currency }).format(
            service.price
          )}
        </p>
        {onBook && (
          <Button size="sm" pressFeedback onClick={() => onBook(service)}>
            Book this service
          </Button>
        )}
      </div>
    </div>
  );
};

export default ServiceCard;
