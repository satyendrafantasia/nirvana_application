package com.nirvana.application.model.dto;

public class BookingCreateResponse {
    private Long bookingId;
    private String bookingReference;
    private String status;
    private String paymentMode;
    private String paymentType;
    private Long packageSubscriptionId;
    private Integer priceCents;
    private Integer taxCents;
    private Integer discountCents;
    private Integer totalCents;
    private String currency;
    private PaymentInitResponse razorpay;
    private UpiPaymentInitResponse upi;

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public Long getPackageSubscriptionId() {
        return packageSubscriptionId;
    }

    public void setPackageSubscriptionId(Long packageSubscriptionId) {
        this.packageSubscriptionId = packageSubscriptionId;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getTaxCents() {
        return taxCents;
    }

    public void setTaxCents(Integer taxCents) {
        this.taxCents = taxCents;
    }

    public Integer getDiscountCents() {
        return discountCents;
    }

    public void setDiscountCents(Integer discountCents) {
        this.discountCents = discountCents;
    }

    public Integer getTotalCents() {
        return totalCents;
    }

    public void setTotalCents(Integer totalCents) {
        this.totalCents = totalCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentInitResponse getRazorpay() {
        return razorpay;
    }

    public void setRazorpay(PaymentInitResponse razorpay) {
        this.razorpay = razorpay;
    }

    public UpiPaymentInitResponse getUpi() {
        return upi;
    }

    public void setUpi(UpiPaymentInitResponse upi) {
        this.upi = upi;
    }
}
