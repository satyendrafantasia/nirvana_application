import { useContext } from 'react';
import { BookingContext } from '../contexts/BookingContext';

export const useBookingFlow = () => useContext(BookingContext);
