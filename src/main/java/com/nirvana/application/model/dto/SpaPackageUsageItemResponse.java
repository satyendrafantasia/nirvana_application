package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public class SpaPackageUsageItemResponse {

    private OffsetDateTime usageDate;
    private Long bookingId;
    private Integer sessionNumber;

    public OffsetDateTime getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(OffsetDateTime usageDate) {
        this.usageDate = usageDate;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Integer getSessionNumber() {
        return sessionNumber;
    }

    public void setSessionNumber(Integer sessionNumber) {
        this.sessionNumber = sessionNumber;
    }
}
