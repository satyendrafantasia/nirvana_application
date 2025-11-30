import clsx from 'clsx';
import { Slot } from '../../types/booking';

interface SlotGridProps {
  slots: Slot[];
  selectedSlot?: string;
  onSelect: (slotIso: string) => void;
}

const SlotGrid = ({ slots, selectedSlot, onSelect }: SlotGridProps) => {
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
      {slots.map((slot) => {
        const isSelected = selectedSlot === slot.startTime;
        return (
          <button
            key={slot.startTime}
            disabled={!slot.available}
            onClick={() => onSelect(slot.startTime)}
            className={clsx(
              'rounded-xl border px-3 py-2 text-sm font-semibold shadow-sm transition',
              slot.available
                ? 'border-primary-100 bg-white hover:border-primary-300'
                : 'cursor-not-allowed border-dashed border-slate-200 text-slate-300',
              isSelected && 'border-primary-500 bg-primary-50 text-primary-700'
            )}
          >
            <div>{new Date(slot.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
            <p className="text-xs text-slate-500">{new Date(slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
          </button>
        );
      })}
    </div>
  );
};

export default SlotGrid;
