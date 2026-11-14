package com.nirvana.application.service;

import com.nirvana.application.model.Booking;

/**
 * Sends booking emails once the change that caused them is safely committed, off the request
 * thread. Nothing is sent if the surrounding transaction rolls back.
 */
public interface NotificationSchedulingService {

    /** Confirmation to the customer and the spa, sent as soon as the booking is confirmed. */
    void scheduleBookingNotifications(Booking booking);

    default void bookingCancelled(Booking booking) {}

    default void bookingRescheduled(Booking booking) {}
}
