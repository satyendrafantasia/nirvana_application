package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.PaymentStatus;
import com.nirvana.application.model.enums.spa.SpaPackageLevel;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;

import java.math.BigDecimal;

public class BuySpaPackageResponse {

    private Long subscriptionId;
    private Long spaId;
    private String spaName;
    private SpaPackageLevel level;
    private BigDecimal pricePaid;
    private Integer totalSessions;
    private Integer remainingSessions;
    private UserSpaPackageStatus status;
    private PaymentStatus paymentStatus;

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public String getSpaName() {
        return spaName;
    }

    public void setSpaName(String spaName) {
        this.spaName = spaName;
    }

    public SpaPackageLevel getLevel() {
        return level;
    }

    public void setLevel(SpaPackageLevel level) {
        this.level = level;
    }

    public BigDecimal getPricePaid() {
        return pricePaid;
    }

    public void setPricePaid(BigDecimal pricePaid) {
        this.pricePaid = pricePaid;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Integer getRemainingSessions() {
        return remainingSessions;
    }

    public void setRemainingSessions(Integer remainingSessions) {
        this.remainingSessions = remainingSessions;
    }

    public UserSpaPackageStatus getStatus() {
        return status;
    }

    public void setStatus(UserSpaPackageStatus status) {
        this.status = status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
