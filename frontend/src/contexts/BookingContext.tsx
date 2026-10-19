import { createContext, ReactNode, useState, useEffect } from 'react';
import { Service } from '../types/spa';
import { useAuth } from '../hooks/useAuth';
import { Slot } from '../types/booking';

interface BookingState {
  spaId?: string;
  spaName?: string;
  service?: Service;
  slot?: string;
  selectedSlot?: Slot;
  guests: number;
  therapistId?: string;
  therapistType?: string;
  notes?: string;
  paymentMode?: 'ONLINE' | 'OFFLINE';
  bookingSource?: string;
}

interface BookingContextValue {
  state: BookingState;
  setSpa: (spaId: string, spaName?: string) => void;
  setService: (service: Service) => void;
  setSlot: (slot?: Slot) => void;
  setGuests: (guests: number) => void;
  setTherapist: (id?: string, type?: string) => void;
  setPaymentMode: (mode: 'ONLINE' | 'OFFLINE') => void;
  setNotes: (notes: string) => void;
  reset: () => void;
}

export const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export const BookingProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const [state, setState] = useState<BookingState>({
    bookingSource: 'WEB',
    guests: 1,
    paymentMode: 'ONLINE'
  });

  useEffect(() => {
    if (!user) setState({ bookingSource: 'WEB', guests: 1, paymentMode: 'ONLINE' });
  }, [user]);
  const setSpa = (spaId: string, spaName?: string) =>
    setState((prev) => ({
      ...prev,
      spaId,
      spaName,
      service: undefined,
      slot: undefined,
      selectedSlot: undefined,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setService = (service: Service) =>
    setState((prev) => ({
      ...prev,
      service,
      guests: service.minPersons || 1,
      slot: undefined,
      selectedSlot: undefined,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setSlot = (selectedSlot?: Slot) =>
    setState((prev) => ({
      ...prev,
      selectedSlot,
      slot: selectedSlot?.startTime,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setGuests = (guests: number) =>
    setState((prev) => ({
      ...prev,
      guests,
      slot: undefined,
      selectedSlot: undefined,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setTherapist = (therapistId?: string, therapistType?: string) =>
    setState((prev) => ({ ...prev, therapistId, therapistType }));
  const setPaymentMode = (paymentMode: 'ONLINE' | 'OFFLINE') =>
    setState((prev) => ({ ...prev, paymentMode }));
  const setNotes = (notes: string) => setState((prev) => ({ ...prev, notes }));
  const reset = () => setState({ bookingSource: 'WEB', guests: 1, paymentMode: 'ONLINE' });

  return (
    <BookingContext.Provider
      value={{
        state,
        setSpa,
        setService,
        setSlot,
        setGuests,
        setTherapist,
        setPaymentMode,
        setNotes,
        reset
      }}
    >
      {children}
    </BookingContext.Provider>
  );
};
