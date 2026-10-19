import { Service } from './spa';
import { PaymentIntent } from './payment';
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
  therapistId?: string;
  therapistType?: string;
  specialRequest?: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  paymentMethod?: string;
  couponCode?: string;
  redeemLoyaltyPoints?: boolean;
  payWithPackage?: boolean;
  payWithSpaPackage?: boolean;
  bookingSource?: string;
}
export interface BookingSummary {
  id: string;
  reference?: string;
  spaId?: string;
  spaName: string;
  service: Service;
  slot: string;
  therapistName?: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  status: string;
  canCancel?: boolean;
  refundStatus?: string;
  totalCents?: number;
  currency?: string;
  razorpay?: PaymentIntent;
}
