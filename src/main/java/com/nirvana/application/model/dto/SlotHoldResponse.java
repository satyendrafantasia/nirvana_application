package com.nirvana.application.model.dto;

import java.time.OffsetDateTime;

public class SlotHoldResponse {

    private String holdToken;
    private OffsetDateTime expiresAt;
    private Long slotId;

    public SlotHoldResponse() {
    }

    public SlotHoldResponse(String holdToken, OffsetDateTime expiresAt, Long slotId) {
        this.holdToken = holdToken;
        this.expiresAt = expiresAt;
        this.slotId = slotId;
    }

    public String getHoldToken() {
        return holdToken;
    }

    public void setHoldToken(String holdToken) {
        this.holdToken = holdToken;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }
}
