import client from './client';
import { Service, Spa } from '../types/spa';
interface SpaWire {
  id: number;
  name: string;
  city?: string;
  formattedAddress?: string;
  addressLine1?: string;
  ratingAvg?: number;
  ratingCount?: number;
  thumbnailImageUrl?: string;
  heroImageUrl?: string;
  distanceKm?: number;
  description?: string;
  openTimeLocal?: string;
  closeTimeLocal?: string;
  workingDays?: string[];
  timezone?: string;
}
const spaView = (s: SpaWire): Spa => ({
  ...s,
  id: String(s.id),
  address: s.formattedAddress || s.addressLine1 || s.city || '',
  rating: s.ratingAvg,
  reviewCount: s.ratingCount,
  coverImage: s.heroImageUrl || s.thumbnailImageUrl
});
export const searchSpas = async (params: Record<string, string | number | undefined>) => {
  const { data } = await client.get<{ content: SpaWire[] }>('/api/spas/search', { params });
  return data.content.map(spaView);
};
export const getSpaDetails = async (id: string) =>
  spaView((await client.get<SpaWire>(`/api/spas/${id}`)).data);
export const getSpaServices = async (id: string): Promise<Service[]> => {
  const { data } = await client.get<
    Array<{
      id: number;
      name: string;
      durationMinutes: number;
      priceCents?: number;
      basePriceCents: number;
      currency: string;
      description?: string;
      isActive: boolean;
      isVisibleOnMarketplace: boolean;
      minPersons?: number;
      maxPersons?: number;
    }>
  >(`/api/spas/${id}/services`);
  return data
    .filter((s) => s.isActive && s.isVisibleOnMarketplace)
    .map((s) => ({ ...s, id: String(s.id), price: (s.priceCents ?? s.basePriceCents) / 100 }));
};
