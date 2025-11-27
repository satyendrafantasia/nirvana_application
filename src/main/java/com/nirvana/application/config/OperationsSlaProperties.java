package com.nirvana.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "operations.sla")
public class OperationsSlaProperties {

    private double availabilityTarget = 0.995;
    private int availabilityWindowDays = 30;
    private double notificationTarget = 0.98;
    private int notificationWindowDays = 7;

    public double getAvailabilityTarget() {
        return availabilityTarget;
    }

    public void setAvailabilityTarget(double availabilityTarget) {
        this.availabilityTarget = availabilityTarget;
    }

    public int getAvailabilityWindowDays() {
        return availabilityWindowDays;
    }

    public void setAvailabilityWindowDays(int availabilityWindowDays) {
        this.availabilityWindowDays = availabilityWindowDays;
    }

    public double getNotificationTarget() {
        return notificationTarget;
    }

    public void setNotificationTarget(double notificationTarget) {
        this.notificationTarget = notificationTarget;
    }

    public int getNotificationWindowDays() {
        return notificationWindowDays;
    }

    public void setNotificationWindowDays(int notificationWindowDays) {
        this.notificationWindowDays = notificationWindowDays;
    }
}
