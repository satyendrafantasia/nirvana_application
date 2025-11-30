import { createContext, ReactNode, useState } from 'react';
import { Service } from '../types/spa';

interface BookingState {
  spaId?: string;
  spaName?: string;
  service?: Service;
  slot?: string;
  therapistId?: string;
  therapistType?: string;
  notes?: string;
  paymentMode?: 'ONLINE' | 'PAY_AT_SPA';
  bookingSource?: string;
}

interface BookingContextValue {
  state: BookingState;
  setSpa: (spaId: string, spaName?: string) => void;
  setService: (service: Service) => void;
  setSlot: (slot: string) => void;
  setTherapist: (id?: string, type?: string) => void;
  setPaymentMode: (mode: 'ONLINE' | 'PAY_AT_SPA') => void;
  setNotes: (notes: string) => void;
  reset: () => void;
}

export const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export const BookingProvider = ({ children }: { children: ReactNode }) => {
  const [state, setState] = useState<BookingState>({ bookingSource: 'WEB' });

  const setSpa = (spaId: string, spaName?: string) => setState((prev) => ({ ...prev, spaId, spaName }));
  const setService = (service: Service) => setState((prev) => ({ ...prev, service }));
  const setSlot = (slot: string) => setState((prev) => ({ ...prev, slot }));
  const setTherapist = (therapistId?: string, therapistType?: string) =>
    setState((prev) => ({ ...prev, therapistId, therapistType }));
  const setPaymentMode = (paymentMode: 'ONLINE' | 'PAY_AT_SPA') =>
    setState((prev) => ({ ...prev, paymentMode }));
  const setNotes = (notes: string) => setState((prev) => ({ ...prev, notes }));
  const reset = () => setState({ bookingSource: 'WEB' });

  return (
    <BookingContext.Provider
      value={{ state, setSpa, setService, setSlot, setTherapist, setPaymentMode, setNotes, reset }}
    >
      {children}
    </BookingContext.Provider>
  );
};
