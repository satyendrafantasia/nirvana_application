import { BookedAddOn, extraTime } from '../../api/addOnApi';
import { money } from '../../api/rewardsApi';

/** The add-ons on a booking, one line each, as they were sold. */
export default function BookedAddOnLines({ addOns }: { addOns?: BookedAddOn[] }) {
  if (!addOns?.length) return null;
  return (
    <div>
      <p className="text-sm text-slate-500">Add-ons</p>
      <ul aria-label="Add-ons" className="space-y-0.5">
        {addOns.map((a, i) => (
          <li key={(a.addOnId ?? a.name) + '-' + i} className="flex justify-between gap-3">
            <span>
              {a.name}
              {a.quantity > 1 && ` × ${a.quantity}`}
              {a.extraMinutes > 0 && <span className="ml-2 text-sm text-slate-500">{extraTime(a.extraMinutes)}</span>}
            </span>
            <span>{money(a.amountCents, a.currency)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
