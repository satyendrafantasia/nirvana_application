package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.BookingStatus;

import java.time.OffsetDateTime;

public class BookingRescheduleResponse {

    private Long bookingId;
    private BookingStatus status;
    private OffsetDateTime newStartTs;
    private OffsetDateTime newEndTs;
    private Integer rescheduleCount;

    public BookingRescheduleResponse() {
    }

    public BookingRescheduleResponse(Long bookingId, BookingStatus status, OffsetDateTime newStartTs, OffsetDateTime newEndTs, Integer rescheduleCount) {
        this.bookingId = bookingId;
        this.status = status;
        this.newStartTs = newStartTs;
        this.newEndTs = newEndTs;
        this.rescheduleCount = rescheduleCount;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public OffsetDateTime getNewStartTs() {
        return newStartTs;
    }

    public void setNewStartTs(OffsetDateTime newStartTs) {
        this.newStartTs = newStartTs;
    }

    public OffsetDateTime getNewEndTs() {
        return newEndTs;
    }

    public void setNewEndTs(OffsetDateTime newEndTs) {
        this.newEndTs = newEndTs;
    }

    public Integer getRescheduleCount() {
        return rescheduleCount;
    }

    public void setRescheduleCount(Integer rescheduleCount) {
        this.rescheduleCount = rescheduleCount;
    }
}
