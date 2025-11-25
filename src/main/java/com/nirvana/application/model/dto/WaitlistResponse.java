package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public class WaitlistResponse {

    private Long id;
    private boolean notified;
    private OffsetDateTime notifiedAt;

    public WaitlistResponse() {
    }

    public WaitlistResponse(Long id, boolean notified, OffsetDateTime notifiedAt) {
        this.id = id;
        this.notified = notified;
        this.notifiedAt = notifiedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public OffsetDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(OffsetDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }
}
