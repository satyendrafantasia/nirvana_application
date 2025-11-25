package com.nirvana.application.service;

import com.nirvana.application.model.Booking;

public interface NotificationSchedulingService {

    void scheduleBookingNotifications(Booking booking);
}
