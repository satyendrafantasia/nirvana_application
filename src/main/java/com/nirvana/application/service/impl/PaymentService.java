package com.nirvana.application.service.impl;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.dto.PaymentLinkInitResponse;
import com.nirvana.application.model.dto.PaymentTimelineEvent;
import com.nirvana.application.model.dto.PaymentTimelineResponse;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.model.dto.UpiPaymentConfirmRequest;
import com.nirvana.application.model.dto.UpiPaymentInitResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.service.CurrencyConversionService;
import com.nirvana.application.service.InvoiceService;
import com.nirvana.application.service.NotificationService;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.security.PaymentMfaVerifier;
import com.razorpay.Order;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import com.razorpay.Utils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import java.util.Objects;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.model.enums.RefundStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

@Service
@Slf4j
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProps;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService; // you already have this
    private final NotificationService notificationService;
    private final CurrencyConversionService currencyConversionService;
    private final NotificationSchedulingService notificationSchedulingService;
    private final PaymentMfaVerifier paymentMfaVerifier;
    private final BookingRefundRecovery refundRecovery;

    public PaymentService(@org.springframework.lang.Nullable RazorpayClient razorpayClient,
                          RazorpayProperties razorpayProps, BookingRepository bookingRepository,
                          PaymentRepository paymentRepository, InvoiceService invoiceService,
                          NotificationService notificationService, CurrencyConversionService currencyConversionService,
                          NotificationSchedulingService notificationSchedulingService, PaymentMfaVerifier paymentMfaVerifier,
                          BookingRefundRecovery refundRecovery) {
        this.refundRecovery = refundRecovery;
        this.razorpayClient = razorpayClient;
        this.razorpayProps = razorpayProps;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
        this.currencyConversionService = currencyConversionService;
        this.notificationSchedulingService = notificationSchedulingService;
        this.paymentMfaVerifier = paymentMfaVerifier;
    }

    private static final String GATEWAY_RAZORPAY = "RAZORPAY";
    private static final String GATEWAY_UPI = "UPI";
    private static final int UPI_EXPIRY_MINUTES = 30;

    @org.springframework.beans.factory.annotation.Value("${reliability.bookings.pending-payment-expiry-minutes:30}")
    private int expiryMinutes = 30;

    public record CheckoutState(BookingStatus status, PaymentMode paymentMode) {}

    @Transactional
    public CheckoutState payAtSpa(Long bookingId) {
        Booking booking = ownedBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED)
            return new CheckoutState(booking.getStatus(), booking.getPaymentMode());
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getPaymentMode() != PaymentMode.ONLINE)
            throw new IllegalStateException("This reservation is no longer pending. Check My bookings before booking again.");

        List<Payment> attempts = paymentRepository.findByBookingId(bookingId);
        boolean hasLink = attempts.stream().anyMatch(p -> p.getPaymentLinkId() != null);
        for (Payment payment : attempts) {
            verifyBeforeSwitch(booking, payment);
            if (booking.getStatus() == BookingStatus.CONFIRMED)
                return new CheckoutState(booking.getStatus(), booking.getPaymentMode());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!booking.getStartTs().isAfter(now) ||
                !BookingLifecycleService.paymentDeadline(booking, hasLink, expiryMinutes).isAfter(now))
            throw new IllegalStateException("This reservation expired. Choose a new appointment time.");
        // Pending bookings already reserve the room, therapist and slot.
        booking.setPaymentMode(PaymentMode.OFFLINE);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setLastStatusChangedAt(now);
        bookingRepository.save(booking);
        notificationSchedulingService.scheduleBookingNotifications(booking);
        return new CheckoutState(booking.getStatus(), booking.getPaymentMode());
    }

    private void verifyBeforeSwitch(Booking booking, Payment payment) {
        if (!GATEWAY_RAZORPAY.equals(payment.getGateway()) || payment.getTransactionId() != null ||
                payment.getPaymentStatus() == PaymentStatus.AUTHORIZED)
            throw new IllegalStateException("A payment is already recorded. Check its status before switching.");
        ensureRazorpayEnabled();
        try {
            for (com.razorpay.Payment attempt : razorpayClient.orders.fetchPayments(payment.getIntentId())) {
                String status = attempt.get("status");
                if ("captured".equals(status)) {
                    settleCapturedPayment(booking, payment, attempt.get("id"));
                    return;
                }
                if (!"failed".equals(status))
                    throw new IllegalStateException("Razorpay is still processing this payment. Please check again shortly.");
            }
            if (payment.getPaymentLinkId() != null) {
                PaymentLink link = razorpayClient.paymentLink.fetch(payment.getPaymentLinkId());
                String status = link.get("status");
                if ("paid".equals(status)) reconcilePaidLink(payment, booking, link);
                else if ("created".equals(status)) {
                    PaymentLink cancelled = razorpayClient.paymentLink.cancel(payment.getPaymentLinkId());
                    if (!"cancelled".equals(cancelled.get("status"))) throw new IllegalStateException("Payment link is still active.");
                } else if (!"cancelled".equals(status) && !"expired".equals(status))
                    throw new IllegalStateException("Payment link is still processing. Please check again shortly.");
            }
        } catch (RazorpayException ex) {
            throw new IllegalStateException("Could not verify the online payment. Please try again shortly.", ex);
        }
    }

    // ------------------ ORDER-BASED CHECKOUT ------------------

    @Transactional
    public PaymentInitResponse initiateRazorpayPayment(Long bookingId, String idempotencyKey) {
        razorpayProps.requireCheckout();
        ensureRazorpayEnabled();
        log.info("event=payment.verify_challenge");
        paymentMfaVerifier.verifyPaymentChallenge();

        Booking booking = ownedBooking(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Booking not in PENDING_PAYMENT: " + booking.getStatus());
        }
        if (booking.getPaymentMode() != PaymentMode.ONLINE) {
            throw new IllegalStateException("Payment can only be initiated for ONLINE mode bookings");
        }

        Optional<Payment> existing = findExistingPayment(bookingId, idempotencyKey, GATEWAY_RAZORPAY);
        if (existing.isPresent()
                && (existing.get().getPaymentStatus() == PaymentStatus.INIT
                || existing.get().getPaymentStatus() == PaymentStatus.PENDING)) {
            log.info("Reusing existing Razorpay order {} for booking {}",
                    existing.get().getIntentId(), bookingId);
            return buildInitResponse(booking, existing.get());
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getPaymentMode() != PaymentMode.ONLINE) {
            throw new IllegalStateException("Only pending online bookings can start payment");
        }
        int amountSubunits = booking.getRemainderCents();
        if (amountSubunits <= 0) {
            throw new IllegalStateException("Invalid amount for booking: " + amountSubunits);
        }

        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountSubunits,
                booking.getCurrency(),
                razorpayProps.getCurrency()
        );
        amountSubunits = conversion.convertedCents();

        try {
            JSONObject orderReq = new JSONObject();
            orderReq.put("amount", amountSubunits);                    // paise
            orderReq.put("currency", razorpayProps.getCurrency());     // "INR"
            orderReq.put("receipt", booking.getBookingReference());
            orderReq.put("payment_capture", razorpayProps.isAutoCapture() ? 1 : 0);

            Order razorpayOrder = razorpayClient.orders.create(orderReq);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setGateway(GATEWAY_RAZORPAY);
            payment.setIdempotencyKey(resolveIdempotencyKey(idempotencyKey));
            payment.setAmountCents(amountSubunits);
            payment.setCurrency(razorpayProps.getCurrency());
            payment.setCurrencyConversionRate(conversion.rateUsed());
            payment.setPaymentStatus(PaymentStatus.INIT);
            payment.setIntentId(razorpayOrder.get("id"));              // order_xxx
            payment.setTotalPrice(
                    BigDecimal.valueOf(amountSubunits).movePointLeft(2));

            paymentRepository.save(payment);

            log.info("Created Razorpay order {} for booking {}", payment.getIntentId(), bookingId);
            return buildInitResponse(booking, payment);
        } catch (RazorpayException e) {
            log.error("Error creating Razorpay order for booking {} errorType={}", bookingId, e.getClass().getSimpleName());
            throw new RuntimeException("Unable to create payment order", e);
        }
    }

    private PaymentInitResponse buildInitResponse(Booking booking, Payment payment) {
        BigDecimal amountMajor = BigDecimal
                .valueOf(payment.getAmountCents())
                .movePointLeft(2);

        return PaymentInitResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .razorpayKeyId(razorpayProps.getKeyId())
                .razorpayOrderId(payment.getIntentId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(amountMajor)
                .currency(payment.getCurrency())
                .description(razorpayProps.getDescriptionPrefix()
                        + " #" + booking.getBookingReference())
                .customerName(booking.getUser().getName())
                .customerEmail(booking.getUser().getEmail())
                .customerPhone(booking.getUser().getPhone())
                .expiresAt(booking.getStartTs().minusMinutes(30))
                .receiptUrl(payment.getReceiptUrl())
                .build();
    }

    // ------------------ CONFIRMATION (CLIENT-SIDE FLOW) ------------------

    @Transactional
    public void confirmRazorpayPayment(Long bookingId, RazorpayConfirmRequest request) {
        log.info("event=payment.verify_challenge");
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = ownedBooking(bookingId);
        Payment payment = paymentRepository.findByIntentId(request.getRazorpayOrderId())
                .filter(p -> Objects.equals(p.getBooking().getId(), bookingId) && GATEWAY_RAZORPAY.equals(p.getGateway()))
                .orElseThrow(() -> new IllegalArgumentException("Order does not belong to this booking"));
        try {
            JSONObject options = new JSONObject()
                    .put("razorpay_order_id", payment.getIntentId())
                    .put("razorpay_payment_id", request.getRazorpayPaymentId())
                    .put("razorpay_signature", request.getRazorpaySignature());
            if (!Utils.verifyPaymentSignature(options, razorpayProps.getKeySecret())) {
                throw new IllegalArgumentException("Invalid payment signature");
            }
            log.info("event=payment.signature_verified");
            settleCapturedPayment(booking, payment, request.getRazorpayPaymentId());
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to verify payment with Razorpay. Please retry.", e);
        }
    }

    /** Called only after webhook authentication; no browser-supplied success flag is trusted. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reconcileCapturedPayment(String orderId, String paymentId) {
        Long bookingId = paymentRepository.findBookingIdByIntentId(orderId).orElse(null);
        if (bookingId == null) return; // Other products and payment links use their own reconciliation paths.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        // Reload after acquiring the booking lock so concurrent checkout/webhook events see committed state.
        Payment payment = paymentRepository.findByIntentId(orderId).orElseThrow();
        settleCapturedPayment(booking, payment, paymentId);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reconcilePendingOrder(String orderId) {
        ensureRazorpayEnabled();
        paymentRepository.findByIntentId(orderId).filter(p -> p.getPaymentLinkId()!=null).ifPresent(p -> reconcilePaymentLink(p.getPaymentLinkId()));
        try {
            for (com.razorpay.Payment remote : razorpayClient.orders.fetchPayments(orderId)) {
                if ("captured".equals(remote.get("status"))) {
                    reconcileCapturedPayment(orderId, remote.get("id"));
                    return;
                }
            }
            // Absence of capture is not evidence of failure. Keep the order retryable.
        } catch (RazorpayException ex) { throw new IllegalStateException("Payment reconciliation unavailable", ex); }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reconcileFailedPayment(String orderId, String paymentId) {
        Long bookingId = paymentRepository.findBookingIdByIntentId(orderId).orElse(null);
        if (bookingId == null) return;
        bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
        Payment payment = paymentRepository.findByIntentId(orderId).orElseThrow();
        if (payment.getPaymentStatus() != PaymentStatus.INIT && payment.getPaymentStatus() != PaymentStatus.PENDING) return;
        try {
            com.razorpay.Payment remote = razorpayClient.payments.fetch(paymentId);
            if (!orderId.equals(remote.get("order_id")) || !"failed".equals(remote.get("status"))) throw new IllegalArgumentException("Unverified payment failure");
            // A failed attempt does not expire the Razorpay order; further attempts can reuse it.
            payment.setPaymentStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
        } catch (RazorpayException ex) { throw new IllegalStateException("Payment verification unavailable", ex); }
    }

    public void reconcileRefund(String paymentId, String refundId) {
        refundRecovery.webhook(paymentId, refundId);
    }

    private void settleCapturedPayment(Booking booking, Payment payment, String paymentId) {
        settleCapturedPayment(booking,payment,paymentId,payment.getIntentId());
    }
    private void settleCapturedPayment(Booking booking, Payment payment, String paymentId, String expectedOrderId) {
        ensureRazorpayEnabled();
        if (!GATEWAY_RAZORPAY.equals(payment.getGateway())) {
            throw new IllegalArgumentException("Not an online Razorpay booking");
        }
        try {
            com.razorpay.Payment remote = razorpayClient.payments.fetch(paymentId);
            if (!paymentId.equals(remote.get("id"))
                    || !expectedOrderId.equals(remote.get("order_id"))
                    || ((Number) remote.get("amount")).longValue() != payment.getAmountCents()
                    || !payment.getCurrency().equalsIgnoreCase(remote.get("currency"))) {
                throw new IllegalArgumentException("Gateway payment does not match the stored order, amount and currency");
            }
            boolean recordedCapture = paymentId.equals(payment.getTransactionId()) && payment.getCapturedAt() != null;
            if (recordedCapture && "refunded".equals(remote.get("status"))) return;
            if (!recordedCapture && ((Number) remote.get("amount_refunded")).longValue() != 0) {
                throw new IllegalStateException("The full payment is required to confirm this appointment.");
            }
            if (!"captured".equals(remote.get("status")) || !Boolean.TRUE.equals(remote.get("captured"))) {
                throw new IllegalStateException("Payment is not captured yet. Please retry verification shortly.");
            }
            if (payment.getTransactionId() != null && !paymentId.equals(payment.getTransactionId())
                    && (payment.getPaymentStatus() == PaymentStatus.CAPTURED || payment.getPaymentStatus() == PaymentStatus.COMPLETED)) {
                throw new IllegalArgumentException("Order already settled by another payment");
            }
            paymentRepository.findByTransactionId(paymentId).ifPresent(other -> {
                if (!Objects.equals(other.getId(), payment.getId())) throw new IllegalArgumentException("Payment already used");
            });
            if (payment.getPaymentStatus() == PaymentStatus.REFUNDED || payment.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) return;
            payment.setTransactionId(paymentId);
            payment.setPaymentStatus(PaymentStatus.CAPTURED);
            if (payment.getCapturedAt() == null) payment.setCapturedAt(OffsetDateTime.now(ZoneOffset.UTC));
            paymentRepository.save(payment);
            // A previously opened checkout can capture after the customer switches.
            // Credit a future appointment online; send a possible double collection at the spa for refund review.
            boolean lateAtSpa = booking.getPaymentMode() == PaymentMode.OFFLINE;
            if (lateAtSpa && booking.getStatus() == BookingStatus.CONFIRMED
                    && booking.getStartTs().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                booking.setPaymentMode(PaymentMode.ONLINE);
                bookingRepository.save(booking);
                lateAtSpa = false;
            }
            if (lateAtSpa || booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW) {
                // Keep released capacity released; record this capture for the existing refund workflow.
                if (!recordedCapture || booking.getRefundStatus() == RefundStatus.REQUESTED)
                    refundRecovery.lateCapture(booking, payment);
                bookingRepository.save(booking);
                return;
            }
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setLastStatusChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
                bookingRepository.save(booking);
                notificationSchedulingService.scheduleBookingNotifications(booking);
            }
            if (payment.getReceiptUrl() == null) {
                var invoice = invoiceService.generateInvoiceForBooking(booking.getId());
                payment.setReceiptUrl(invoice.pdfUrl());
                paymentRepository.save(payment);
            }
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to verify payment with Razorpay. Please retry.", e);
        }
    }

    // ------------------ UPI (QR / DEEPLINK) ------------------

    /** Legacy direct-to-spa UPI cannot be independently verified. UPI remains available in Razorpay Checkout. */
    @Transactional
    public UpiPaymentInitResponse initiateUpiPayment(Long bookingId, String idempotencyKey) {
        ownedBooking(bookingId);
        throw new IllegalStateException("Direct UPI is unavailable. Use Razorpay Checkout to pay by UPI.");
    }

    @Transactional
    public void confirmUpiPayment(Long bookingId, UpiPaymentConfirmRequest request) {
        ownedBooking(bookingId);
        throw new IllegalStateException("Client-reported UPI success is not accepted. Use verified Razorpay Checkout.");
    }

    // ------------------ RETRY PAYMENT ------------------

    /**
     * Retry flow: create a new order if previous one failed/expired.
     */
    @Transactional
    public PaymentInitResponse retryRazorpayPayment(Long bookingId, String idempotencyKey) {
        log.info("event=payment.verify_challenge");
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = ownedBooking(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Booking not in PENDING_PAYMENT: " + booking.getStatus());
        }

        // If there's a INIT/PENDING payment but very old, you can mark it as EXPIRED here if you want.

        // Just call the same init method; idempotency there will reuse or create fresh:
        return initiateRazorpayPayment(bookingId, idempotencyKey);
    }

    // ------------------ REFUND FLOW ------------------

    /**
     * Refund (partial or full) via Razorpay.
     * refundAmountCents == null → full refund.
     */
    @Transactional
    public void refundBookingPayment(Long bookingId, Integer refundAmountCents, String reason) {
        ownedBooking(bookingId);
        refundAuthorizedBookingPayment(bookingId, refundAmountCents, reason);
    }

    /** Caller has authorized the refund for this booking. Submission happens after commit. */
    @Transactional
    public void refundAuthorizedBookingPayment(Long bookingId, Integer refundAmountCents, String reason) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
        Payment payment = paymentRepository.findFirstByBookingIdAndPaymentStatusIn(bookingId,
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED))
                .orElseThrow(() -> new IllegalStateException("No captured payment for this booking"));
        refundRecovery.enqueue(booking, payment, refundAmountCents == null ? BookingRefundRecovery.remaining(payment) : refundAmountCents, reason);
    }

    // ------------------ PAYMENT LINK FALLBACK ------------------

    /**
     * Alternative: create Razorpay Payment Link if you want to send payment URL via SMS/email.
     */
    @Transactional
    public PaymentLinkInitResponse createPaymentLinkForBooking(Long bookingId, String idempotencyKey) {
        razorpayProps.requireCheckout();
        ensureRazorpayEnabled();
        Booking booking = ownedBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getPaymentMode() != PaymentMode.ONLINE)
            throw new IllegalStateException("Only pending online bookings can start payment");
        Payment payment = paymentRepository.findByBookingIdAndGateway(bookingId, GATEWAY_RAZORPAY).orElseThrow(() -> new EntityNotFoundException("Payment order not found"));
        try {
            PaymentLink link;
            if (payment.getPaymentLinkId() != null) {
                link = razorpayClient.paymentLink.fetch(payment.getPaymentLinkId());
                if ("paid".equals(link.get("status"))) reconcilePaidLink(payment, booking, link);
                if ("cancelled".equals(link.get("status")) || "expired".equals(link.get("status"))) throw new IllegalStateException("Payment link expired or cancelled. Use booking checkout instead.");
            } else {
                long expiry = booking.getStartTs().minusMinutes(30).toEpochSecond();
                if (expiry <= OffsetDateTime.now().plusMinutes(1).toEpochSecond()) throw new IllegalStateException("Too close to the appointment for a payment link. Use booking checkout.");
                JSONObject req = new JSONObject().put("amount", payment.getAmountCents()).put("currency", payment.getCurrency())
                    .put("reference_id", booking.getBookingReference()).put("description", "Spa appointment " + booking.getBookingReference())
                    .put("accept_partial", false).put("expire_by", expiry).put("notify",new JSONObject().put("sms",false).put("email",false));
                link = razorpayClient.paymentLink.create(req);
                payment.setPaymentLinkId(link.get("id"));paymentRepository.save(payment);
            }
            return PaymentLinkInitResponse.builder().bookingId(bookingId).bookingReference(booking.getBookingReference())
                .razorpayPaymentLinkId(link.get("id")).shortUrl(link.get("short_url")).status(link.get("status"))
                .amount(BigDecimal.valueOf(payment.getAmountCents()).movePointLeft(2)).currency(payment.getCurrency())
                .expireBy(OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(((Number)link.get("expire_by")).longValue()),ZoneOffset.UTC)).build();
        } catch (RazorpayException ex) { throw new IllegalStateException("Payment link unavailable", ex); }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reconcilePaymentLink(String linkId) {
        var known = paymentRepository.findByPaymentLinkId(linkId);
        if (known.isEmpty()) return;
        Booking booking = bookingRepository.findByIdForUpdate(known.get().getBooking().getId()).orElseThrow();
        Payment payment = paymentRepository.findByPaymentLinkId(linkId).orElseThrow();
        ensureRazorpayEnabled();
        try { reconcilePaidLink(payment, booking, razorpayClient.paymentLink.fetch(linkId)); }
        catch (RazorpayException ex) { throw new IllegalStateException("Payment link verification unavailable", ex); }
    }

    private void reconcilePaidLink(Payment payment, Booking booking, PaymentLink link) throws RazorpayException {
        if (!Objects.equals(payment.getPaymentLinkId(),link.get("id")) || !Objects.equals(booking.getBookingReference(),link.get("reference_id"))
            || ((Number)link.get("amount")).longValue()!=payment.getAmountCents() || !Objects.equals(payment.getCurrency(),link.get("currency")))
            throw new IllegalArgumentException("Payment link does not match this booking");
        if (!"paid".equals(link.get("status"))) return;
        if (((Number)link.get("amount_paid")).longValue()!=payment.getAmountCents()) throw new IllegalStateException("Full payment is required");
        org.json.JSONArray paid = link.get("payments");
        if (paid == null || paid.length()!=1) throw new IllegalStateException("Payment link needs manual reconciliation");
        String paymentId=paid.getJSONObject(0).getString("payment_id");
        String orderId=link.get("order_id");
        if (orderId==null || orderId.isBlank()) throw new IllegalStateException("Payment link order missing");
        payment.setPaymentLinkOrderId(orderId);
        settleCapturedPayment(booking,payment,paymentId,orderId);
    }

    // ------------------ VISIBILITY ------------------

    @Transactional
    public PaymentTimelineResponse getPaymentTimelineForBooking(Long bookingId) {
        Booking booking = ownedBooking(bookingId);
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        if (payments.isEmpty()) {
            throw new EntityNotFoundException("No payments recorded for booking " + bookingId);
        }

        List<PaymentTimelineEvent> events = new ArrayList<>();
        payments.forEach(payment -> {
            events.add(new PaymentTimelineEvent(
                    "INIT",
                    payment.getGateway() + " intent created",
                    Optional.ofNullable(payment.getCreatedAt()).orElse(booking.getCreatedAt())));
            if (payment.getPaymentStatus() == PaymentStatus.COMPLETED || payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
                events.add(new PaymentTimelineEvent(
                        "CAPTURED",
                        "Payment authorized/captured via " + payment.getGateway(),
                        Optional.ofNullable(payment.getCapturedAt()).orElse(payment.getUpdatedAt())));
            }
            if (payment.getRefundedAt() != null) {
                events.add(new PaymentTimelineEvent(
                        "REFUNDED",
                        "Refunded " + payment.getRefundedCents() + " cents",
                        payment.getRefundedAt()));
            }
        });

        var invoice = invoiceService.getInvoiceEntityByBookingId(bookingId);
        if (invoice != null) {
            events.add(new PaymentTimelineEvent(
                    "INVOICE",
                    "Invoice issued: " + invoice.getInvoiceNumber(),
                    invoice.getIssuedAt()));
        }

        events.sort(Comparator.comparing(PaymentTimelineEvent::occurredAt));

        Payment first = payments.get(0);
        return new PaymentTimelineResponse(
                bookingId,
                booking.getBookingReference(),
                first.getGateway(),
                events
        );
    }

    @Transactional
    public String fetchReceiptUrl(Long bookingId) {
        Booking booking = ownedBooking(bookingId);

        Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for booking: " + bookingId));

        if (payment.getPaymentStatus() != PaymentStatus.CAPTURED && payment.getPaymentStatus() != PaymentStatus.COMPLETED
                && payment.getPaymentStatus() != PaymentStatus.REFUNDED && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Receipt is available only after verified payment");
        }
        if (payment.getReceiptUrl() != null && payment.getReceiptUrl().equals("/api/bookings/" + bookingId + "/invoice/pdf")) {
            return payment.getReceiptUrl();
        }

        var invoice = invoiceService.generateInvoiceForBooking(bookingId);
        payment.setReceiptUrl(invoice.pdfUrl());
        paymentRepository.save(payment);
        return invoice.pdfUrl();
    }

    // ------------------ INTERNAL ------------------

    private Optional<Payment> findExistingPayment(Long bookingId, String idempotencyKey, String gateway) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Payment> byKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) {
                Payment existing = byKey.get();
                if (!Objects.equals(existing.getBooking().getId(), bookingId) || !gateway.equals(existing.getGateway())) {
                    throw new IllegalArgumentException("Idempotency key belongs to another payment");
                }
                return byKey;
            }
        }
        return paymentRepository.findTopByBookingIdAndGatewayOrderByCreatedAtDesc(bookingId, gateway);
    }

    private String resolveIdempotencyKey(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, String> paymentStatus(Long bookingId) {
        Booking booking = accessibleBooking(bookingId);
        return java.util.Map.of("status", booking.getStatus().name(), "refundStatus", booking.getRefundStatus().name());
    }

    public void requireBookingAccess(Long bookingId) {
        accessibleBooking(bookingId);
    }

    private Booking accessibleBooking(Long bookingId) {
        if (platformAdmin()) return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        return bookingRepository.findByIdAndUserId(bookingId, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AccessDeniedException("Booking access denied"));
    }

    private Booking ownedBooking(Long bookingId) {
        if (platformAdmin()) return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        return bookingRepository.findOwnedForUpdate(bookingId, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AccessDeniedException("Booking access denied"));
    }

    private boolean platformAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }

    private void ensureRazorpayEnabled() {
        if (razorpayClient == null || !razorpayProps.isEnabled()) {
            throw new IllegalStateException("Razorpay not configured");
        }
    }
}
