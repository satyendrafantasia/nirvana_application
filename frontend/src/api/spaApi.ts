import client from './client';
import { Service, Spa } from '../types/spa';

export const searchSpas = async (params: Record<string, string | number | undefined>) => {
  const { data } = await client.get<Spa[]>('/api/spas', { params });
  return data;
};

export const getSpaDetails = async (spaId: string) => {
  const { data } = await client.get<Spa>(`/api/spas/${spaId}`);
  return data;
};

export const getSpaServices = async (spaId: string) => {
  const { data } = await client.get<Service[]>(`/api/spas/${spaId}/services`);
  return data;
};
