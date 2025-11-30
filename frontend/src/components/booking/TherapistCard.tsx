import clsx from 'clsx';
import { Therapist } from '../../types/booking';

interface TherapistCardProps {
  therapist: Therapist;
  selected?: boolean;
  onSelect: (id: string) => void;
}

const TherapistCard = ({ therapist, selected, onSelect }: TherapistCardProps) => {
  return (
    <button
      onClick={() => onSelect(therapist.id)}
      className={clsx(
        'flex w-full items-center justify-between rounded-2xl border bg-white p-4 text-left shadow-sm transition hover:border-primary-300',
        selected ? 'border-primary-500 bg-primary-50' : 'border-gray-200'
      )}
    >
      <div>
        <p className="text-base font-semibold text-slate-800">{therapist.name}</p>
        <p className="text-sm text-slate-500">{therapist.specialty || 'Therapist'}</p>
        {therapist.experienceYears && (
          <p className="text-xs text-slate-500">{therapist.experienceYears}+ years experience</p>
        )}
      </div>
      {therapist.rating && (
        <span className="rounded-full bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700">
          ⭐ {therapist.rating.toFixed(1)}
        </span>
      )}
    </button>
  );
};

export default TherapistCard;
