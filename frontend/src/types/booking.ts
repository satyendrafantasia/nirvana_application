import { Service } from './spa';

export interface Slot {
  startTime: string;
  endTime: string;
  available: boolean;
}

export interface Therapist {
  id: string;
  name: string;
  experienceYears?: number;
  rating?: number;
  specialty?: string;
}

export interface BookingPayload {
  spaId: string;
  serviceId: string;
  slot: string;
  therapistId?: string;
  therapistType?: string;
  notes?: string;
  paymentMode: 'ONLINE' | 'PAY_AT_SPA';
  bookingSource?: string;
}

export interface BookingSummary {
  id: string;
  spaName: string;
  service: Service;
  slot: string;
  therapistName?: string;
  paymentMode: 'ONLINE' | 'PAY_AT_SPA';
  status: string;
}
