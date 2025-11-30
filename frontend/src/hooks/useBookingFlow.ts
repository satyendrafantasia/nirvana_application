import { useContext } from 'react';
import { BookingContext } from '../contexts/BookingContext';

export const useBookingFlow = () => {
  const ctx = useContext(BookingContext);
  if (!ctx) throw new Error('useBookingFlow must be used within BookingProvider');
  return ctx;
};
