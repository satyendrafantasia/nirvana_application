import raw from './catalog.json';
export interface Schema {
  $ref?: string;
  type?: string;
  format?: string;
  enum?: string[];
  properties?: Record<string, Schema>;
  items?: Schema;
  required?: string[];
  additionalProperties?: Schema | boolean;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  default?: unknown;
  description?: string;
}
export interface Operation {
  title: string;
  method: string;
  path: string;
  parameters: { name: string; in: string; required?: boolean; schema: Schema }[];
  body?: Schema;
  multipart: boolean;
}
export interface Feature {
  id: string;
  title: string;
  access: string;
  description: string;
  actions: Operation[];
}
export const features = raw.features as Feature[];
export const schemas = raw.schemas as Record<string, Schema>;
export const resolve = (s: Schema): Schema =>
  s.$ref ? schemas[s.$ref.split('/').pop()!] || {} : s;
const labels: Record<string, string> = {
  spaId: 'Spa',
  bookingId: 'Appointment',
  serviceId: 'Service',
  slotId: 'Appointment time',
  threadId: 'Conversation',
  notificationId: 'Notification',
  corporateId: 'Company',
  dealId: 'Company deal',
  couponId: 'Coupon',
  subscriptionId: 'Purchased package',
  spaPackageId: 'Spa package',
  lat: 'Latitude',
  lon: 'Longitude',
  originLat: 'Starting latitude',
  originLon: 'Starting longitude',
  radiusKm: 'Search radius (km)',
  text: 'Your review',
  body: 'Message',
  confirmationKeyword: 'Type ERASE to confirm',
  paymentReference: 'Verified payment reference',
  paymentMethod: 'Payment method',
  consentVersion: 'Privacy policy version',
  basePriceCents: 'Base price',
  overridePriceCents: 'Override price',
  amountCents: 'Amount',
  priceCents: 'Price'
};
export const label = (key: string) =>
  labels[key] ||
  key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, (s) => s.toUpperCase());
export const clean = (value: unknown, schema: Schema, key = ''): unknown => {
  const s = resolve(schema);
  if (value === '' || value === undefined || value === null) return undefined;
  if (s.type === 'object' || s.properties) {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (!s.properties?.[k] && !s.additionalProperties) continue;
      const result = clean(
        v,
        s.properties?.[k] ||
          (typeof s.additionalProperties === 'object' ? s.additionalProperties : {}),
        k
      );
      if (result !== undefined) out[k] = result;
    }
    return Object.keys(out).length ? out : undefined;
  }
  if (s.type === 'array')
    return (value as unknown[])
      .map((v) => clean(v, s.items || {}, key))
      .filter((v) => v !== undefined);
  if (s.type === 'integer' || s.type === 'number')
    return key.endsWith('Cents') ? Math.round(Number(value) * 100) : Number(value);
  if (s.format === 'date-time') return new Date(String(value)).toISOString();
  return value;
};
export function buildRequest(op: Operation, values: Record<string, unknown>) {
  let url = op.path;
  const params: Record<string, unknown> = {};
  for (const p of op.parameters) {
    const v = clean(values[p.name] ?? p.schema.default, p.schema, p.name);
    if (p.required && v === undefined) throw new Error(`Choose ${label(p.name).toLowerCase()}.`);
    if (p.in === 'path') url = url.replace(`{${p.name}}`, encodeURIComponent(String(v)));
    else if (v !== undefined) params[p.name] = v;
  }
  const data = op.body ? clean(values, op.body) : undefined;
  return { url, method: op.method, params, data };
}
