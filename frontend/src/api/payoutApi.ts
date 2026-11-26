import client from './client';

export type PayoutStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

/** One payout statement: online money in, less refunds, less commission. Amounts are in paise. */
export type Payout = {
  id: number;
  spaId: number;
  spaName: string;
  reference: string;
  currency: string;
  status: PayoutStatus;
  periodStart?: string | null;
  periodEnd?: string | null;
  gross: number;
  refunds: number;
  commissionPct: number;
  commission: number;
  net: number;
  provider?: string | null;
  transferId?: string | null;
  failureReason?: string | null;
  attempts: number;
  lastAttemptAt?: string | null;
  paidAt?: string | null;
  bankAccount?: string | null;
  createdAt: string;
};

export type PayoutItem = {
  kind: 'BOOKING' | 'BOOKING_REFUND' | 'PACKAGE' | 'PACKAGE_REFUND';
  reference?: string;
  amount: number;
  occurredAt: string;
};

export type PayoutAccount = {
  accountHolder: string;
  ifsc: string;
  accountMasked: string;
  businessType: string;
  routeAccountId?: string | null;
  routeStatus: 'NOT_LINKED' | 'LINKED' | 'FAILED';
  routeError?: string | null;
  updatedAt: string;
};

export type Upcoming = {
  currency: string;
  gross: number;
  refunds: number;
  commissionPct: number;
  commission: number;
  net: number;
  items: number;
};

export type OwnerPayouts = {
  automatic: boolean;
  account: PayoutAccount | null;
  upcoming: Upcoming[];
  items: Payout[];
  hasMore: boolean;
};

export type AdminPayouts = { mode: string; automatic: boolean; items: Payout[]; hasMore: boolean };

export type BankDetails = {
  accountHolder: string;
  ifsc: string;
  accountNumber: string;
  businessType: string;
};

export const BUSINESS_TYPES: [string, string][] = [
  ['proprietorship', 'Sole proprietorship'],
  ['partnership', 'Partnership'],
  ['llp', 'LLP'],
  ['private_limited', 'Private limited company'],
  ['public_limited', 'Public limited company'],
  ['individual', 'Individual'],
  ['trust', 'Trust'],
  ['society', 'Society'],
  ['not_yet_registered', 'Not yet registered']
];

export const money = (paise: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(
    paise / 100
  );

/** Server times are UTC without a zone. */
export const utc = (v?: string | null) =>
  v ? new Date(/[zZ+]/.test(v.slice(10)) ? v : v + 'Z') : null;
export const day = (v?: string | null) =>
  utc(v)?.toLocaleDateString('en-IN', { dateStyle: 'medium', timeZone: 'Asia/Kolkata' }) ?? '—';

/** The statement's period: the cut-off is exclusive, so the last day shown is the day before it. */
export const period = (p: Pick<Payout, 'periodStart' | 'periodEnd'>) => {
  const end = utc(p.periodEnd);
  if (!end) return '—';
  const last = new Date(end.getTime() - 1);
  return `${day(p.periodStart)} – ${day(last.toISOString())}`;
};

export const statusLabel = (p: Pick<Payout, 'status' | 'transferId'>) =>
  ({
    PENDING: 'Waiting to be paid',
    IN_PROGRESS: 'Transfer in progress',
    COMPLETED: 'Paid',
    FAILED: 'Transfer failed'
  })[p.status] ?? p.status;

export const kindLabel = (k: PayoutItem['kind']) =>
  ({
    BOOKING: 'Online booking payment',
    BOOKING_REFUND: 'Booking refund',
    PACKAGE: 'Package sale',
    PACKAGE_REFUND: 'Package refund'
  })[k] ?? k;

/** IFSC: four letters, a zero, six letters or digits. */
export const validIfsc = (v: string) => /^[A-Z]{4}0[A-Z0-9]{6}$/.test(v.trim().toUpperCase());
export const accountDigits = (v: string) => v.replace(/[\s-]/g, '');
export const validAccount = (v: string) => /^[0-9]{9,18}$/.test(accountDigits(v));

export const ownerPayouts = async (spa: string, page = 0) =>
  (await client.get<OwnerPayouts>(`/api/owner/spas/${spa}/payouts`, { params: { page } })).data;
export const ownerStatement = async (spa: string, id: number) =>
  (await client.get<Payout & { items: PayoutItem[] }>(`/api/owner/spas/${spa}/payouts/${id}`)).data;
export const saveBankDetails = async (spa: string, details: BankDetails) =>
  (
    await client.put<PayoutAccount>(`/api/owner/spas/${spa}/payouts/account`, {
      ...details,
      ifsc: details.ifsc.trim().toUpperCase(),
      accountNumber: accountDigits(details.accountNumber)
    })
  ).data;

export const adminPayouts = async (status: string, page = 0) =>
  (
    await client.get<AdminPayouts>('/api/admin/payouts', {
      params: { status: status || undefined, page }
    })
  ).data;
export const adminStatement = async (id: number) =>
  (
    await client.get<Payout & { items: PayoutItem[]; account: PayoutAccount | null }>(
      `/api/admin/payouts/${id}`
    )
  ).data;
export const runSettlement = async () =>
  (
    await client.post<{
      cutoff: string;
      created: number;
      carriedForward: number;
      sent: number;
      completed: number;
      failed: number;
    }>('/api/admin/payouts/run')
  ).data;
export const retryPayout = async (id: number) =>
  (await client.post<Payout>(`/api/admin/payouts/${id}/retry`)).data;
export const markPayoutPaid = async (id: number, reference: string) =>
  (await client.post<Payout>(`/api/admin/payouts/${id}/mark-paid`, { reference })).data;
export const setLinkedAccount = async (spa: number, accountId: string) =>
  (await client.put<PayoutAccount>(`/api/admin/payouts/spas/${spa}/linked-account`, { accountId }))
    .data;
