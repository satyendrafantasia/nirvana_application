package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "BookingCreateRequest", description = "Payload to create a booking for a spa service.")
public class BookingCreateRequest {

    @NotNull
    @Schema(description = "Spa identifier where booking is being created", example = "101")
    private Long spaId;

    @NotNull
    @Schema(description = "Service identifier for the booking", example = "501")
    private Long serviceId;

    @NotNull
    @Schema(description = "Selected slot identifier", example = "9001")
    private Long slotId;

    @NotNull
    @Min(1)
    @Schema(description = "Number of guests included in booking", example = "1")
    private Integer guests;

    @Schema(description = "Preferred therapist identifier if applicable", example = "300")
    private Long therapistId;

    @Size(max = 128)
    @Schema(description = "Requested therapist type", example = "FEMALE")
    private String therapistType;

    @NotNull
    @Schema(description = "Payment mode for the booking", example = "ONLINE")
    private String paymentMode; // ONLINE | OFFLINE

    @Schema(description = "Preferred online payment method", example = "RAZORPAY")
    private String paymentMethod;

    @Size(max = 2000)
    @Schema(description = "Special request notes for the spa", example = "Need a quiet room")
    private String specialRequest;

    @Size(max = 64)
    @Schema(description = "Coupon code applied to booking", example = "WELCOME50")
    private String couponCode;

    @Schema(description = "Whether loyalty points should be redeemed", example = "true")
    private Boolean redeemLoyaltyPoints;

    @Schema(description = "Hold token for reserved slot if applicable")
    private String holdToken;

    @Schema(description = "Whether to consume an available package instead of direct payment", example = "false")
    private Boolean payWithPackage;

    @Schema(description = "Source of booking traffic for analytics", example = "GOOGLE_MAPS")
    private String bookingSource;

    public Long getSpaId() {
        return spaId;
    }

    public void setSpaId(Long spaId) {
        this.spaId = spaId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Integer getGuests() {
        return guests;
    }

    public void setGuests(Integer guests) {
        this.guests = guests;
    }

    public Long getTherapistId() {
        return therapistId;
    }

    public void setTherapistId(Long therapistId) {
        this.therapistId = therapistId;
    }

    public String getTherapistType() {
        return therapistType;
    }

    public void setTherapistType(String therapistType) {
        this.therapistType = therapistType;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getSpecialRequest() {
        return specialRequest;
    }

    public void setSpecialRequest(String specialRequest) {
        this.specialRequest = specialRequest;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public Boolean getRedeemLoyaltyPoints() {
        return redeemLoyaltyPoints;
    }

    public void setRedeemLoyaltyPoints(Boolean redeemLoyaltyPoints) {
        this.redeemLoyaltyPoints = redeemLoyaltyPoints;
    }

    public String getHoldToken() {
        return holdToken;
    }

    public void setHoldToken(String holdToken) {
        this.holdToken = holdToken;
    }

    public Boolean getPayWithPackage() {
        return payWithPackage;
    }

    public void setPayWithPackage(Boolean payWithPackage) {
        this.payWithPackage = payWithPackage;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource(String bookingSource) {
        this.bookingSource = bookingSource;
    }
}
