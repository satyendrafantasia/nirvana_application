package com.nirvana.application.service;

import com.nirvana.application.model.Booking;

public interface NotificationService {
    void notifySpaOwnerBookingConfirmed(Booking booking);

    void notifyCustomerBookingConfirmed(Booking booking);
}
