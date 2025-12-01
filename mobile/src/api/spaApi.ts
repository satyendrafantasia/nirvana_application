import client from './client';
import { SpaDetails, SpaSummary, Service } from '../types/spa';

export interface SpaSearchParams {
  query?: string;
  location?: string;
  date?: string;
}

export const searchSpas = (params: SpaSearchParams) =>
  client.get<SpaSummary[]>('/spas', { params }).then(res => res.data);

export const getSpaDetails = (spaId: string) =>
  client.get<SpaDetails>(`/spas/${spaId}`).then(res => res.data);

export const getSpaServices = (spaId: string) =>
  client.get<Service[]>(`/spas/${spaId}/services`).then(res => res.data);
