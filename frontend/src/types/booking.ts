import { Service } from './spa';
import { PaymentIntent } from './payment';
import { BookedAddOn } from '../api/addOnApi';
export interface Therapist {
  id: string;
  name: string;
  type?: string;
  available: boolean;
  experienceYears?: number;
  rating?: number;
  specialty?: string;
}
export interface Slot {
  id: string;
  startTime: string;
  endTime: string;
  available: boolean;
  therapistSelectionEnabled: boolean;
  therapistTypeSelectionEnabled: boolean;
  therapists: Therapist[];
  therapistTypes: { type: string; availableCount: number }[];
}
export interface BookingPayload {
  holdToken?: string;
  spaId: string;
  serviceId: string;
  slotId: string;
  guests: number;
  /** The server checks and prices them; never send a price. */
  addOnIds?: number[];
  therapistId?: string;
  therapistType?: string;
  specialRequest?: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  paymentMethod?: string;
  couponCode?: string;
  redeemLoyaltyPoints?: boolean;
  giftCardCode?: string;
  payWithPackage?: boolean;
  payWithSpaPackage?: boolean;
  useWalletCredit?: boolean;
  expectedWalletCents?: number;
  bookingSource?: string;
}
export interface BookingSummary {
  id: string;
  reference?: string;
  spaId?: string;
  spaName: string;
  service: Service;
  slot: string;
  originalPreference?: string;
  therapistName?: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  status: string;
  canCancel?: boolean;
  invoiceAvailable?: boolean;
  spaAcknowledged?: boolean;
  refundStatus?: string;
  payAtSpaCheckoutCents?: number;
  totalCents?: number;
  currency?: string;
  /** Each add-on on its own line, as sold. */
  addOns?: BookedAddOn[];
  razorpay?: PaymentIntent;
}
