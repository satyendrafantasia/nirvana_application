package com.nirvana.application.service.impl;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.User;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulingServiceImpl implements NotificationSchedulingService {

    private final NotificationService notificationService;
    private final TaskScheduler taskScheduler;

    @Override
    public void scheduleBookingNotifications(Booking booking) {
        OffsetDateTime start = booking.getStartTs();
        if (start == null) {
            notificationService.notifySpaOwnerBookingConfirmed(booking);
            notificationService.notifyCustomerBookingConfirmed(booking);
            return;
        }

        ZonedDateTime customerSendTime = computeLocalSendTime(start, Optional.ofNullable(booking.getUser()).map(User::getTimezone).orElse(null));
        ZonedDateTime spaSendTime = computeLocalSendTime(start, booking.getSpa() != null ? booking.getSpa().getTimezone() : null);

        scheduleOrRunNow(customerSendTime, () -> notificationService.notifyCustomerBookingConfirmed(booking));
        scheduleOrRunNow(spaSendTime, () -> notificationService.notifySpaOwnerBookingConfirmed(booking));
    }

    private ZonedDateTime computeLocalSendTime(OffsetDateTime start, String timezoneOverride) {
        String timezone = Optional.ofNullable(timezoneOverride).orElse("UTC");
        try {
            return start.atZoneSameInstant(ZoneId.of(timezone)).minusMinutes(30);
        } catch (Exception e) {
            log.warn("Falling back to UTC scheduling for timezone {}: {}", timezone, e.getMessage());
            return start.atZoneSameInstant(ZoneId.of("UTC")).minusMinutes(30);
        }
    }

    private void scheduleOrRunNow(ZonedDateTime scheduledTime, Runnable task) {
        if (scheduledTime.isBefore(ZonedDateTime.now())) {
            task.run();
            return;
        }
        taskScheduler.schedule(task, Date.from(scheduledTime.toInstant()));
    }
}
