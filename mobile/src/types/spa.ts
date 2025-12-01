export interface SpaSummary {
  id: string;
  name: string;
  address: string;
  rating?: number;
  reviewCount?: number;
  coverImageUrl?: string;
  distanceKm?: number;
}

export interface SpaDetails extends SpaSummary {
  description?: string;
  openingHours?: string;
  phone?: string;
  placeId?: string;
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
  currency: string;
  description?: string;
}
