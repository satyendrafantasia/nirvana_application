export interface Spa {
  id: string;
  name: string;
  address: string;
  description?: string;
  openTimeLocal?: string;
  closeTimeLocal?: string;
  workingDays?: string[];
  timezone?: string;
  city?: string;
  rating?: number;
  reviewCount?: number;
  coverImage?: string;
  distanceKm?: number;
  categories?: string[];
  phone?: string;
  email?: string;
  websiteUrl?: string;
  latitude?: number;
  longitude?: number;
  googlePlaceId?: string;
  defaultCurrency?: string;
  amenities?: string[];
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
  currency: string;
  minPersons?: number;
  maxPersons?: number;
  description?: string;
}
