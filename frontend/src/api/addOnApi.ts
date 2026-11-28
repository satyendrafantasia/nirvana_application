import client from './client';

/** An add-on or upgrade a guest can choose with a treatment, such as "+30 min" or hot stones. */
export interface AddOn {
  id: number;
  name: string;
  description?: string;
  /** Per guest, like the treatment price. */
  priceCents: number;
  currency: string;
  /** Added once to the appointment, however many guests. */
  extraMinutes: number;
}

/** The owner's view: also inactive add-ons, the treatments they apply to (none means all) and the edit version. */
export interface OwnerAddOn extends AddOn {
  spaId: number;
  active: boolean;
  sortOrder: number;
  serviceIds: number[];
  version: number;
}

export interface OwnerAddOnInput {
  name: string;
  description?: string;
  priceCents: number;
  currency: string;
  extraMinutes: number;
  active: boolean;
  sortOrder: number;
  serviceIds: number[];
  version?: number;
}

/** An add-on on a booking, as it was sold. */
export interface BookedAddOn {
  addOnId?: number;
  name: string;
  unitPriceCents: number;
  quantity: number;
  amountCents: number;
  currency: string;
  extraMinutes: number;
}

export const getServiceAddOns = async (spaId: string, serviceId: string) =>
  (await client.get<AddOn[]>(`/api/spas/${spaId}/add-ons`, { params: { serviceId } })).data;

export const listOwnerAddOns = async (spaId: string) =>
  (await client.get<OwnerAddOn[]>(`/api/owner/spas/${spaId}/add-ons`)).data;

export const saveOwnerAddOn = async (spaId: string, input: OwnerAddOnInput, id?: number) =>
  (
    await client.request<OwnerAddOn>({
      method: id ? 'PUT' : 'POST',
      url: `/api/owner/spas/${spaId}/add-ons` + (id ? '/' + id : ''),
      data: input
    })
  ).data;

export const deleteOwnerAddOn = async (spaId: string, id: number) =>
  client.delete(`/api/owner/spas/${spaId}/add-ons/${id}`);

/**
 * What the chosen add-ons add, as the server will price them: each add-on's price for every guest,
 * and its minutes once. Only for showing a running total; the server's checkout preview is the price.
 */
export function addOnTotals(addOns: AddOn[], guests: number) {
  const count = Math.max(1, guests);
  return addOns.reduce(
    (sum, a) => ({ cents: sum.cents + a.priceCents * count, minutes: sum.minutes + a.extraMinutes }),
    { cents: 0, minutes: 0 }
  );
}

/** "+30 min" for an extension, nothing for an oil. */
export const extraTime = (minutes: number) => (minutes > 0 ? `+${minutes} min` : '');

/** Query parameters for the chosen add-ons: "addOnIds=3,7", which the server reads as a list. */
export const addOnParams = (ids: number[]) => (ids.length ? { addOnIds: ids.join(',') } : {});
