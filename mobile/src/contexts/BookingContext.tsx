import React, { createContext, useMemo, useState } from 'react';
import { Service } from '../types/spa';
import { Slot, Therapist } from '../types/booking';

type TherapistPreference = 'ANY' | 'MALE' | 'FEMALE' | 'SPECIFIC';

type BookingContextValue = {
  spaId?: string;
  spaName?: string;
  selectedService?: Service;
  selectedDate?: string;
  selectedSlot?: Slot;
  therapistType?: TherapistPreference;
  selectedTherapist?: Therapist;
  paymentMethod?: 'ONLINE' | 'PAY_AT_SPA';
  bookingSource?: 'MOBILE_APP' | 'GOOGLE_MAPS';
  setSpa: (id: string, name?: string) => void;
  setService: (service: Service) => void;
  setDate: (date: string) => void;
  setSlot: (slot: Slot) => void;
  setTherapistPreference: (preference: TherapistPreference, therapist?: Therapist) => void;
  setPaymentMethod: (method: 'ONLINE' | 'PAY_AT_SPA') => void;
  reset: () => void;
};

export const BookingContext = createContext<BookingContextValue>({
  setSpa: () => undefined,
  setService: () => undefined,
  setDate: () => undefined,
  setSlot: () => undefined,
  setTherapistPreference: () => undefined,
  setPaymentMethod: () => undefined,
  reset: () => undefined
});

const initialState = {
  spaId: undefined,
  spaName: undefined,
  selectedService: undefined,
  selectedDate: undefined,
  selectedSlot: undefined,
  therapistType: undefined,
  selectedTherapist: undefined,
  paymentMethod: undefined,
  bookingSource: 'MOBILE_APP' as const
};

export const BookingProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState(initialState);

  const setSpa = (id: string, name?: string) => setState(prev => ({ ...prev, spaId: id, spaName: name }));

  const setService = (service: Service) => setState(prev => ({ ...prev, selectedService: service }));

  const setDate = (date: string) => setState(prev => ({ ...prev, selectedDate: date }));

  const setSlot = (slot: Slot) => setState(prev => ({ ...prev, selectedSlot: slot }));

  const setTherapistPreference = (preference: TherapistPreference, therapist?: Therapist) =>
    setState(prev => ({ ...prev, therapistType: preference, selectedTherapist: therapist }));

  const setPaymentMethod = (method: 'ONLINE' | 'PAY_AT_SPA') =>
    setState(prev => ({ ...prev, paymentMethod: method }));

  const reset = () => setState(initialState);

  const value = useMemo(
    () => ({
      ...state,
      setSpa,
      setService,
      setDate,
      setSlot,
      setTherapistPreference,
      setPaymentMethod,
      reset
    }),
    [state]
  );

  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
};
