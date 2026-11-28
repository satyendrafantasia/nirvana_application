import { useQuery } from '@tanstack/react-query';
import { AddOn, addOnTotals, extraTime, getServiceAddOns } from '../../api/addOnApi';
import { errorMessage } from '../../api/client';
import { money } from '../../api/rewardsApi';

interface AddOnPickerProps {
  spaId: string;
  serviceId: string;
  /** The treatment's own length, to show how long the appointment becomes. */
  durationMinutes?: number;
  guests: number;
  selected: AddOn[];
  onChange: (addOns: AddOn[]) => void;
}

/** Adds or removes one add-on, keeping the spa's menu order. */
export function toggleAddOn(menu: AddOn[], selected: AddOn[], addOn: AddOn): AddOn[] {
  const chosen = new Set(selected.map((a) => a.id));
  if (chosen.has(addOn.id)) chosen.delete(addOn.id);
  else chosen.add(addOn.id);
  return menu.filter((a) => chosen.has(a.id));
}

/**
 * The add-ons the spa offers with this treatment. Prices are per guest; the total here is a guide,
 * and the checkout preview, priced by the server, is what is charged.
 */
export default function AddOnPicker({ spaId, serviceId, durationMinutes, guests, selected, onChange }: AddOnPickerProps) {
  const menu = useQuery({
    queryKey: ['service-add-ons', spaId, serviceId],
    queryFn: () => getServiceAddOns(spaId, serviceId),
    enabled: Boolean(spaId && serviceId)
  });
  if (menu.isError) return <p role="alert">Unable to load add-ons. {errorMessage(menu.error)}</p>;
  if (!menu.data?.length) return null;
  const chosen = new Set(selected.map((a) => a.id));
  const toggle = (addOn: AddOn) => onChange(toggleAddOn(menu.data!, selected, addOn));
  const totals = addOnTotals(selected, guests);
  return (
    <fieldset className="space-y-2 rounded-2xl border border-gray-100 p-4">
      <legend className="px-1 text-lg font-semibold text-slate-900">Add-ons and upgrades</legend>
      <p className="text-sm text-slate-600">Optional extras for your treatment{guests > 1 ? ', priced per guest' : ''}.</p>
      {menu.data.map((addOn) => (
        <label key={addOn.id} className="flex items-start gap-3 rounded-xl p-2 hover:bg-gray-50">
          <input type="checkbox" className="mt-1" checked={chosen.has(addOn.id)} onChange={() => toggle(addOn)} />
          <span className="flex-1">
            <span className="font-medium text-slate-900">{addOn.name}</span>
            {addOn.extraMinutes > 0 && <span className="ml-2 text-sm text-slate-600">{extraTime(addOn.extraMinutes)}</span>}
            {addOn.description && <span className="block text-sm text-slate-500">{addOn.description}</span>}
          </span>
          <span className="text-sm font-medium text-slate-900">+{money(addOn.priceCents, addOn.currency)}</span>
        </label>
      ))}
      {selected.length > 0 && (
        <p className="text-sm text-slate-700" role="status">
          Add-ons: {money(totals.cents, selected[0].currency)}
          {totals.minutes > 0 &&
            ` · appointment ${durationMinutes ? `${durationMinutes + totals.minutes} min` : extraTime(totals.minutes)}`}
        </p>
      )}
    </fieldset>
  );
}
