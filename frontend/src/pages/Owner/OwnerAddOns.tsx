import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import {
  deleteOwnerAddOn,
  extraTime,
  listOwnerAddOns,
  OwnerAddOn,
  OwnerAddOnInput,
  saveOwnerAddOn
} from '../../api/addOnApi';
import { money } from '../../api/rewardsApi';
import Button from '../../components/common/Button';
import { showToast } from '../../components/common/Toast';

interface Form {
  name: string;
  description: string;
  /** In rupees (major units), as the owner types it. */
  price: string;
  currency: string;
  extraMinutes: string;
  active: boolean;
  sortOrder: string;
  serviceIds: number[];
}

const blank = (currency: string): Form => ({
  name: '',
  description: '',
  price: '',
  currency,
  extraMinutes: '0',
  active: true,
  sortOrder: '0',
  serviceIds: []
});

/** Whole minor units from what the owner typed, or NaN. */
export const toCents = (price: string) => {
  const value = Number(price.trim());
  return price.trim() && Number.isFinite(value) && value >= 0 ? Math.round(value * 100) : NaN;
};

/**
 * The spa's add-ons and upgrades ("+30 min", hot stones, aromatherapy oil): what guests can add to a
 * treatment, at what price and for how many extra minutes. Owners and managers only.
 */
export default function OwnerAddOns({ spaId }: { spaId: string }) {
  const qc = useQueryClient();
  const key = ['owner-add-ons', spaId];
  const addOns = useQuery({ queryKey: key, queryFn: () => listOwnerAddOns(spaId) });
  const services = useQuery({
    queryKey: ['owner-catalog', spaId, 'services'],
    queryFn: async () =>
      (await client.get<{ id: number; name: string; currency: string }[]>(`/api/owner/spas/${spaId}/services`)).data
  });
  const currency = services.data?.[0]?.currency || 'INR';
  const [form, setForm] = useState<Form>(blank(currency));
  const [editing, setEditing] = useState<OwnerAddOn>();
  const cents = toCents(form.price);
  const minutes = Number(form.extraMinutes);
  const valid = form.name.trim() !== '' && !Number.isNaN(cents) && Number.isInteger(minutes) && minutes >= 0 && minutes <= 240;
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: key });
    void qc.invalidateQueries({ queryKey: ['service-add-ons', spaId] });
  };
  const save = useMutation({
    mutationFn: () => {
      const input: OwnerAddOnInput = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        priceCents: cents,
        currency: (editing ? form.currency : currency).toUpperCase(),
        extraMinutes: minutes,
        active: form.active,
        sortOrder: Number(form.sortOrder) || 0,
        serviceIds: form.serviceIds,
        version: editing?.version
      };
      return saveOwnerAddOn(spaId, input, editing?.id);
    },
    onSuccess: () => {
      showToast('Add-on saved.', 'success');
      setForm(blank(currency));
      setEditing(undefined);
      refresh();
    }
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteOwnerAddOn(spaId, id),
    onSuccess: () => refresh()
  });
  const serviceName = (id: number) => services.data?.find((s) => s.id === id)?.name ?? 'Treatment ' + id;
  const edit = (a: OwnerAddOn) => {
    setEditing(a);
    setForm({
      name: a.name,
      description: a.description ?? '',
      price: (a.priceCents / 100).toFixed(2),
      currency: a.currency,
      extraMinutes: String(a.extraMinutes),
      active: a.active,
      sortOrder: String(a.sortOrder),
      serviceIds: a.serviceIds
    });
  };
  const toggleService = (id: number) =>
    setForm((f) => ({
      ...f,
      serviceIds: f.serviceIds.includes(id) ? f.serviceIds.filter((s) => s !== id) : [...f.serviceIds, id]
    }));
  return (
    <section className="grid lg:grid-cols-2 gap-6" aria-labelledby="add-ons-heading">
      <div className="card p-6 space-y-4">
        <h2 id="add-ons-heading" className="text-2xl font-bold">
          Add-ons and upgrades
        </h2>
        <p className="text-sm">
          Extras guests can choose when they book, such as hot stones or a 30-minute extension. Extra minutes
          lengthen the appointment, so the therapist and room are kept free for the whole time.
        </p>
        {addOns.isPending && <p>Loading…</p>}
        {addOns.isError && <p role="alert">{errorMessage(addOns.error)}</p>}
        {remove.isError && <p role="alert">{errorMessage(remove.error)}</p>}
        {addOns.data?.length === 0 && <p>No add-ons yet. Add your first one.</p>}
        {addOns.data?.map((a) => (
          <article className="border rounded-xl p-4 space-y-2" key={a.id}>
            <h3 className="font-semibold">{a.name}</h3>
            <p>
              {money(a.priceCents, a.currency)} per guest
              {a.extraMinutes > 0 && ' · ' + extraTime(a.extraMinutes)} · {a.active ? 'Active' : 'Inactive'}
            </p>
            <p className="text-sm text-slate-600">
              {a.serviceIds.length ? 'For ' + a.serviceIds.map(serviceName).join(', ') : 'For every treatment'}
            </p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => edit(a)}>
                Edit
              </Button>
              <Button
                variant="ghost"
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm(`Delete ${a.name}? Past bookings keep their copy.`)) remove.mutate(a.id);
                }}
              >
                Delete
              </Button>
            </div>
          </article>
        ))}
      </div>
      <form
        className="card p-6 space-y-3"
        aria-label={editing ? 'Edit add-on' : 'New add-on'}
        onSubmit={(e) => {
          e.preventDefault();
          if (valid) save.mutate();
        }}
      >
        <h3 className="text-xl font-semibold">{editing ? `Edit ${editing.name}` : 'New add-on'}</h3>
        <label className="block">
          Name
          <input
            className="block border rounded-xl p-2 w-full"
            required
            maxLength={120}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </label>
        <label className="block">
          Description
          <textarea
            className="block border rounded-xl p-2 w-full"
            maxLength={1000}
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </label>
        <label className="block">
          Price per guest ({editing ? form.currency : currency})
          <input
            className="block border rounded-xl p-2 w-full"
            inputMode="decimal"
            required
            value={form.price}
            onChange={(e) => setForm({ ...form, price: e.target.value })}
          />
        </label>
        <label className="block">
          Extra minutes
          <input
            className="block border rounded-xl p-2 w-full"
            type="number"
            min={0}
            max={240}
            step={5}
            value={form.extraMinutes}
            onChange={(e) => setForm({ ...form, extraMinutes: e.target.value })}
          />
          <span className="text-sm text-slate-600">0 for things like oils; 30 for a 30-minute extension.</span>
        </label>
        <label className="block">
          Order on the menu
          <input
            className="block border rounded-xl p-2 w-full"
            type="number"
            min={0}
            value={form.sortOrder}
            onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
          />
        </label>
        <label className="block">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Offer
          this add-on
        </label>
        <fieldset className="space-y-1">
          <legend>Treatments (none ticked means every treatment)</legend>
          {services.data?.map((s) => (
            <label className="block" key={s.id}>
              <input type="checkbox" checked={form.serviceIds.includes(s.id)} onChange={() => toggleService(s.id)} /> {s.name}
            </label>
          ))}
        </fieldset>
        {!valid && form.price !== '' && Number.isNaN(cents) && <p role="alert">Enter a price of 0 or more.</p>}
        {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
        <div className="flex gap-2">
          <Button type="submit" disabled={!valid || save.isPending}>
            {editing ? 'Save add-on' : 'Add add-on'}
          </Button>
          {editing && (
            <Button
              variant="secondary"
              type="button"
              onClick={() => {
                setEditing(undefined);
                setForm(blank(currency));
              }}
            >
              Cancel
            </Button>
          )}
        </div>
      </form>
    </section>
  );
}
