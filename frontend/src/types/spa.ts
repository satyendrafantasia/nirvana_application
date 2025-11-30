export interface Spa {
  id: string;
  name: string;
  address: string;
  city?: string;
  rating?: number;
  reviewCount?: number;
  coverImage?: string;
  distanceKm?: number;
  categories?: string[];
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
  description?: string;
}
