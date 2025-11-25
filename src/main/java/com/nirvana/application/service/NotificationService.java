package com.nirvana.application.service;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.WaitlistEntry;
import com.nirvana.application.model.Slot;

public interface NotificationService {
    void notifySpaOwnerBookingConfirmed(Booking booking);

    void notifyCustomerBookingConfirmed(Booking booking);

    default void notifyCustomerBookingCancelled(Booking booking) {}

    default void notifySpaOwnerBookingCancelled(Booking booking) {}

    default void notifyWaitlistUserSlotAvailable(WaitlistEntry entry, Slot slot) {}
}
