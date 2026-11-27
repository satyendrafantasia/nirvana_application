import { createContext, ReactNode, useState, useEffect } from 'react';
import { Service } from '../types/spa';
import { useAuth } from '../hooks/useAuth';
import { Slot } from '../types/booking';

interface BookingState {
  holdToken?: string;
  holdExpiresAt?: string;
  heldTherapistName?: string;
  spaId?: string;
  spaName?: string;
  service?: Service;
  availabilityDate?: string;
  slot?: string;
  selectedSlot?: Slot;
  guests: number;
  therapistId?: string;
  preferredTherapistName?: string;
  therapistType?: string;
  notes?: string;
  paymentMode?: 'ONLINE' | 'OFFLINE';
  bookingSource?: string;
}

interface BookingContextValue {
  state: BookingState;
  setSpa: (spaId: string, spaName?: string) => void;
  setService: (service: Service) => void;
  setAvailabilityDate: (date: string) => void;
  setSlot: (slot?: Slot) => void;
  setGuests: (guests: number) => void;
  setTherapist: (id?: string, type?: string, publicName?: string) => void;
  setPaymentMode: (mode: 'ONLINE' | 'OFFLINE') => void;
  setNotes: (notes: string) => void;
  setHold: (token: string, expiresAt: string, name?: string) => void;
  reset: () => void;
}

export const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export function withSelectedSlot(state: BookingState, selectedSlot?: Slot): BookingState {
  return {
    ...state,
    selectedSlot,
    slot: selectedSlot?.startTime,
    holdToken: undefined,
    heldTherapistName: undefined,
    holdExpiresAt: undefined
  };
}

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
      ...withSelectedSlot(prev),
      spaId,
      spaName,
      service: undefined,
      availabilityDate: undefined,
      preferredTherapistName: undefined,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setService = (service: Service) =>
    setState((prev) => ({
      ...withSelectedSlot(prev),
      service,
      availabilityDate: new Date().toLocaleDateString('en-CA'),
      preferredTherapistName: undefined,
      guests: service.minPersons || 1,
      therapistId: undefined,
      therapistType: undefined
    }));
  const setSlot = (selectedSlot?: Slot) =>
    setState(prev => withSelectedSlot(prev, selectedSlot));
  const setGuests = (guests: number) =>
    setState(prev => ({ ...withSelectedSlot(prev), guests }));
  const setAvailabilityDate = (availabilityDate: string) =>
    setState(prev => ({ ...withSelectedSlot(prev), availabilityDate }));
  const setTherapist = (therapistId?: string, therapistType?: string, preferredTherapistName?: string) =>
    setState((prev) => ({ ...prev, therapistId, therapistType, preferredTherapistName }));
  const setPaymentMode = (paymentMode: 'ONLINE' | 'OFFLINE') =>
    setState((prev) => ({ ...prev, paymentMode }));
  const setNotes = (notes: string) => setState((prev) => ({ ...prev, notes }));
  const setHold = (holdToken: string, holdExpiresAt: string, heldTherapistName?: string) =>
    setState((prev) => ({ ...prev, holdToken, holdExpiresAt, heldTherapistName }));
  const reset = () => setState({ bookingSource: 'WEB', guests: 1, paymentMode: 'ONLINE' });

  return (
    <BookingContext.Provider
      value={{
        state,
        setHold,
        setSpa,
        setService,
        setAvailabilityDate,
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
