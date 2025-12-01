import { Service } from './spa';

export interface Slot {
  id: string;
  startTime: string;
  endTime: string;
  available: boolean;
}

export interface Therapist {
  id: string;
  name: string;
  gender?: 'MALE' | 'FEMALE';
  avatarUrl?: string;
}

export interface BookingPayload {
  spaId: string;
  serviceId: string;
  slotId: string;
  therapistId?: string;
  paymentMethod: 'ONLINE' | 'PAY_AT_SPA';
  source?: 'MOBILE_APP' | 'GOOGLE_MAPS';
}

export interface BookingSummary {
  id: string;
  spaName: string;
  serviceName: string;
  date: string;
  status: 'CONFIRMED' | 'CANCELLED' | 'PENDING';
  amount: number;
  currency: string;
}

export interface BookingReviewData {
  spaId: string;
  spaName?: string;
  selectedService?: Service;
  selectedSlot?: Slot;
  selectedTherapist?: Therapist;
  paymentMethod?: 'ONLINE' | 'PAY_AT_SPA';
}
