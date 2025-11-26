package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotNull;

public class PackageUsageConsumeRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    private Long spaId;

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }
}
