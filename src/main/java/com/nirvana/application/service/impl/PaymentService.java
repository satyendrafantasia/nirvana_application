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
import com.razorpay.Refund;
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

@Service
@RequiredArgsConstructor
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

    private static final String GATEWAY_RAZORPAY = "RAZORPAY";
    private static final String GATEWAY_UPI = "UPI";
    private static final int UPI_EXPIRY_MINUTES = 30;

    // ------------------ ORDER-BASED CHECKOUT ------------------

    @Transactional
    public PaymentInitResponse initiateRazorpayPayment(Long bookingId, String idempotencyKey) {
        ensureRazorpayEnabled();
        paymentMfaVerifier.verifyPaymentChallenge();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

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
            log.error("Error creating Razorpay order for booking {}", bookingId, e);
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
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (booking.getPaymentMode() != PaymentMode.ONLINE) {
            throw new IllegalStateException("Cannot confirm payment for OFFLINE bookings");
        }

        Payment payment = paymentRepository
                .findByBookingIdAndGateway(bookingId, GATEWAY_RAZORPAY)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for booking " + bookingId));

        if (!requireNonNull(payment.getIntentId()).equals(request.getRazorpayOrderId())) {
            throw new IllegalArgumentException("Order id mismatch");
        }

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED
                || payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            // idempotent
            return;
        }

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean valid = Utils.verifyPaymentSignature(
                    options, razorpayProps.getKeySecret());

            if (!valid) {
                log.warn("Invalid Razorpay signature for booking {}", bookingId);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw new IllegalArgumentException("Invalid payment signature");
            }

            if (paymentRepository.existsByTransactionId(request.getRazorpayPaymentId())) {
                log.info("Payment {} already processed, skipping", request.getRazorpayPaymentId());
                return;
            }

            payment.setPaymentStatus(
                    razorpayProps.isAutoCapture() ? PaymentStatus.CAPTURED : PaymentStatus.COMPLETED);
            payment.setTransactionId(request.getRazorpayPaymentId());
            paymentRepository.save(payment);

            BookingStatus previousStatus = booking.getStatus();
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setLastStatusChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
            bookingRepository.save(booking);

            // Generate invoice (idempotent)
            var invoice = invoiceService.generateInvoiceForBooking(bookingId);
            payment.setReceiptUrl(invoice.pdfUrl());
            paymentRepository.save(payment);

            if (previousStatus != BookingStatus.CONFIRMED) {
                notificationSchedulingService.scheduleBookingNotifications(booking);
            }

            log.info("Payment {} confirmed for booking {}",
                    payment.getTransactionId(), bookingId);
        } catch (RazorpayException e) {
            log.error("Error verifying Razorpay signature for booking {}", bookingId, e);
            throw new RuntimeException("Error verifying payment", e);
        }
    }

    // ------------------ UPI (QR / DEEPLINK) ------------------

    @Transactional
    public UpiPaymentInitResponse initiateUpiPayment(Long bookingId, String idempotencyKey) {
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Booking not in PENDING_PAYMENT: " + booking.getStatus());
        }
        if (booking.getPaymentMode() != PaymentMode.ONLINE) {
            throw new IllegalStateException("Payment can only be initiated for ONLINE mode bookings");
        }

        Spa spa = booking.getSpa();
        SpaManager manager = spa.getSpaManager();
        if (manager == null || manager.getUpiId() == null || manager.getUpiId().isBlank()) {
            throw new IllegalStateException("UPI is not configured for this spa");
        }

        Optional<Payment> existing = findExistingPayment(bookingId, idempotencyKey, GATEWAY_UPI);
        if (existing.isPresent()
                && (existing.get().getPaymentStatus() == PaymentStatus.INIT
                || existing.get().getPaymentStatus() == PaymentStatus.PENDING)) {
            return buildUpiInitResponse(booking, manager, existing.get());
        }

        int amountSubunits = booking.getRemainderCents();
        if (amountSubunits <= 0) {
            throw new IllegalStateException("Invalid amount for booking: " + amountSubunits);
        }

        String intentId = "upi_" + booking.getBookingReference();
        if (paymentRepository.existsByIntentId(intentId)) {
            intentId = intentId + "_" + UUID.randomUUID();
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setGateway(GATEWAY_UPI);
        payment.setIdempotencyKey(resolveIdempotencyKey(idempotencyKey));
        payment.setAmountCents(amountSubunits);
        payment.setCurrency(booking.getCurrency());
        payment.setPaymentStatus(PaymentStatus.INIT);
        payment.setIntentId(intentId);
        payment.setPaymentMethod("UPI");
        payment.setTotalPrice(BigDecimal.valueOf(amountSubunits).movePointLeft(2));

        paymentRepository.save(payment);

        return buildUpiInitResponse(booking, manager, payment);
    }

    @Transactional
    public void confirmUpiPayment(Long bookingId, UpiPaymentConfirmRequest request) {
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (booking.getPaymentMode() != PaymentMode.ONLINE) {
            throw new IllegalStateException("Cannot confirm payment for OFFLINE bookings");
        }

        Payment payment = paymentRepository
                .findByBookingIdAndGateway(bookingId, GATEWAY_UPI)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for booking " + bookingId));

        if (!requireNonNull(payment.getIntentId()).equals(request.getPaymentIntentId())) {
            throw new IllegalArgumentException("Intent id mismatch");
        }

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED
                || payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            return;
        }

        if (!request.isSuccess()) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return;
        }

        if (paymentRepository.existsByTransactionId(request.getTransactionReference())) {
            log.info("UPI transaction {} already processed, skipping", request.getTransactionReference());
            return;
        }

        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(request.getTransactionReference());
        payment.setBankTxnId(request.getPayerVpa());
        payment.setCapturedAt(OffsetDateTime.now(ZoneOffset.UTC));
        paymentRepository.save(payment);

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setLastStatusChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bookingRepository.save(booking);

        var invoice = invoiceService.generateInvoiceForBooking(bookingId);
        payment.setReceiptUrl(invoice.pdfUrl());
        paymentRepository.save(payment);

        if (previousStatus != BookingStatus.CONFIRMED) {
            notificationSchedulingService.scheduleBookingNotifications(booking);
        }
    }

    private UpiPaymentInitResponse buildUpiInitResponse(Booking booking, SpaManager manager, Payment payment) {
        BigDecimal amountMajor = BigDecimal
                .valueOf(payment.getAmountCents())
                .movePointLeft(2);

        String note = "Booking #" + booking.getBookingReference();
        String payeeName = booking.getSpa().getName();
        String deepLink = buildUpiDeepLink(manager.getUpiId(), payeeName, amountMajor, payment.getCurrency(),
                payment.getIntentId(), note);

        OffsetDateTime expiresAt = booking.getStartTs() != null
                ? booking.getStartTs().minusMinutes(UPI_EXPIRY_MINUTES)
                : OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(UPI_EXPIRY_MINUTES);

        return UpiPaymentInitResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .amount(amountMajor)
                .currency(payment.getCurrency())
                .payeeName(payeeName)
                .upiId(manager.getUpiId())
                .upiDeepLink(deepLink)
                .qrImageUrl(manager.getUpiQrImageUrl())
                .transactionRef(payment.getIntentId())
                .expiresAt(expiresAt)
                .build();
    }

    private String buildUpiDeepLink(String upiId, String payeeName, BigDecimal amount, String currency,
                                    String transactionRef, String note) {
        StringBuilder builder = new StringBuilder("upi://pay?");
        builder.append("pa=").append(encode(upiId));
        builder.append("&pn=").append(encode(payeeName));
        builder.append("&am=").append(amount);
        builder.append("&cu=").append(encode(currency));
        builder.append("&tr=").append(encode(transactionRef));
        builder.append("&tn=").append(encode(note));
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ------------------ RETRY PAYMENT ------------------

    /**
     * Retry flow: create a new order if previous one failed/expired.
     */
    @Transactional
    public PaymentInitResponse retryRazorpayPayment(Long bookingId, String idempotencyKey) {
        paymentMfaVerifier.verifyPaymentChallenge();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

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
    public Refund refundBookingPayment(Long bookingId, Integer refundAmountCents, String reason) {
        ensureRazorpayEnabled();

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusIn(
                        bookingId,
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.CAPTURED))
                .orElseThrow(() -> new IllegalStateException(
                        "No successful payment to refund for booking " + bookingId));

        String paymentId = payment.getTransactionId();
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalStateException("Payment has no transaction id to refund");
        }

        try {
            JSONObject refundReq = new JSONObject();
            if (refundAmountCents != null && refundAmountCents > 0) {
                refundReq.put("amount", refundAmountCents);
            }
            if (reason != null && !reason.isBlank()) {
                refundReq.put("notes", new JSONObject().put("reason", reason));
            }

            Refund refund = razorpayClient.payments.refund(paymentId, refundReq);

            // You can store refund id/status in Payment.metaJson or create PaymentRefund entity.
            log.info("Created refund {} for booking {} (payment {})",
                    refund.get("id"), bookingId, paymentId);

            // Optionally: update booking status → CANCELLED, and mark payment as REFUNDED/PARTIALLY_REFUNDED
            // based on refund.get("amount") vs payment.getAmountCents()

            return refund;
        } catch (RazorpayException e) {
            log.error("Error refunding Razorpay payment for booking {}", bookingId, e);
            throw new RuntimeException("Error creating refund", e);
        }
    }

    // ------------------ PAYMENT LINK FALLBACK ------------------

    /**
     * Alternative: create Razorpay Payment Link if you want to send payment URL via SMS/email.
     */
    @Transactional
    public PaymentLinkInitResponse createPaymentLinkForBooking(Long bookingId, String idempotencyKey) {
        ensureRazorpayEnabled();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        int amountSubunits = booking.getRemainderCents();
        if (amountSubunits <= 0) {
            throw new IllegalStateException("Invalid amount for booking: " + amountSubunits);
        }

        try {
            JSONObject req = new JSONObject();
            req.put("amount", amountSubunits);
            req.put("currency", razorpayProps.getCurrency());
            req.put("description", razorpayProps.getDescriptionPrefix()
                    + " #" + booking.getBookingReference());
            req.put("reference_id", booking.getBookingReference());

            // Customer info
            JSONObject customer = new JSONObject();
            customer.put("name", booking.getUser().getName());
            customer.put("email", booking.getUser().getEmail());
            customer.put("contact", booking.getUser().getPhone());
            req.put("customer", customer);

            // Expiry example: 30 mins before service start
            long expireByEpoch = booking.getStartTs()
                    .minusMinutes(30)
                    .toEpochSecond();
            req.put("expire_by", expireByEpoch);

            // Optional: reminders, notifications config...

            PaymentLink plink = razorpayClient.paymentLink.create(req);

            BigDecimal amountMajor = BigDecimal
                    .valueOf(amountSubunits)
                    .movePointLeft(2);

            PaymentLinkInitResponse response = PaymentLinkInitResponse.builder()
                    .bookingId(booking.getId())
                    .bookingReference(booking.getBookingReference())
                    .razorpayPaymentLinkId(plink.get("id"))
                    .shortUrl(plink.get("short_url"))
                    .status(plink.get("status"))
                    .amount(amountMajor)
                    .currency(razorpayProps.getCurrency())
                    .expireBy(OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(expireByEpoch),
                            ZoneOffset.UTC))
                    .build();

            paymentRepository.findByBookingIdAndGateway(bookingId, GATEWAY_RAZORPAY)
                    .ifPresentOrElse(payment -> {
                        payment.setIdempotencyKey(resolveIdempotencyKey(idempotencyKey));
                        payment.setReceiptUrl(plink.get("short_url"));
                        paymentRepository.save(payment);
                    }, () -> log.debug("No primary payment record to attach link idempotency for booking {}", bookingId));

            return response;
        } catch (RazorpayException e) {
            log.error("Error creating Razorpay payment link for booking {}", bookingId, e);
            throw new RuntimeException("Error creating payment link", e);
        }
    }

    // ------------------ VISIBILITY ------------------

    @Transactional(readOnly = true)
    public PaymentTimelineResponse getPaymentTimelineForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for booking: " + bookingId));

        if (payment.getReceiptUrl() != null) {
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
                return byKey;
            }
        }
        return paymentRepository.findByBookingIdAndGateway(bookingId, gateway);
    }

    private String resolveIdempotencyKey(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }

    private void ensureRazorpayEnabled() {
        if (razorpayClient == null || !razorpayProps.isEnabled()) {
            throw new IllegalStateException("Razorpay not configured");
        }
    }
}
