import { BookingSummary } from './booking';

export interface UserProfile {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  preferredCity?: string;
}

export interface UserBookingsResponse {
  items: BookingSummary[];
}
