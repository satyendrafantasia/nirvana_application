type OwnerBooking = {
  bookingId: number;
  bookingReference: string;
  startTs: string;
  endTs: string;
  status: string;
  paymentMode?: string;
  serviceName?: string;
  customerName?: string;
  customerPhone?: string;
  therapistName?: string;
};
const CHECK_IN_WINDOW_MS = 120 * 60 * 1000;
/** Front-desk actions the server will accept for this appointment right now. */
export const bookingActions = (b: Pick<OwnerBooking, 'status' | 'startTs'>, now = Date.now()) => {
  const start = Date.parse(b.startTs);
  const started = now >= start;
  const actions: { path: string; label: string }[] = [];
  if (b.status === 'CONFIRMED' && now >= start - CHECK_IN_WINDOW_MS)
    actions.push({ path: 'arrive', label: 'Check in' });
  if ((b.status === 'CONFIRMED' || b.status === 'ARRIVED') && started)
    actions.push({ path: 'complete', label: 'Mark completed' });
  if (b.status === 'CONFIRMED' && started) actions.push({ path: 'no-show', label: 'No-show' });
  return actions;
};
/** The spa can cancel an open booking until the appointment has ended. */
export const canSpaCancel = (b: Pick<OwnerBooking, 'status' | 'endTs'>, now = Date.now()) =>
  (b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT') && now < Date.parse(b.endTs);
