package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.dto.BookingInitiationDTO;

public interface PaymentService {

    Payment savePayment(BookingInitiationDTO bookingInitiationDTO, Booking booking);
}
