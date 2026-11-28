package com.nirvana.application.groups;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.dto.PaymentInitResponse;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.service.InvoiceService;
import com.nirvana.application.service.NotificationSchedulingService;
import com.nirvana.application.service.impl.BookingRefundRecovery;
import com.nirvana.application.service.impl.PaymentService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One Razorpay order and payment for a whole group. On capture every guest booking gets its own
 * payment row for its share (amount = that booking's payable), so receipts, GST invoices, payouts and
 * refunds stay per booking. The rows name the shared provider payment in {@code shared_gateway_payment_id}.
 */
@Service
@Slf4j
public class GroupCheckout {
    private final RazorpayClient razorpay;
    private final RazorpayProperties properties;
    private final JdbcTemplate jdbc;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final NotificationSchedulingService notifications;
    private final InvoiceService invoices;
    private final BookingRefundRecovery refunds;

    @Value("${reliability.bookings.pending-payment-expiry-minutes:30}")
    private int expiryMinutes = 30;

    public GroupCheckout(@Nullable RazorpayClient razorpay, RazorpayProperties properties, JdbcTemplate jdbc,
                         BookingRepository bookings, PaymentRepository payments, NotificationSchedulingService notifications,
                         InvoiceService invoices, BookingRefundRecovery refunds) {
        this.razorpay = razorpay;
        this.properties = properties;
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.payments = payments;
        this.notifications = notifications;
        this.invoices = invoices;
        this.refunds = refunds;
    }

    /** The group a booking belongs to, or null for an ordinary booking. */
    public Long groupOf(Long bookingId) {
        if (bookingId == null) return null;
        var ids = jdbc.queryForList("SELECT group_id FROM booking_group_member WHERE booking_id=?", Long.class, bookingId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> lockGroup(long groupId) {
        return jdbc.queryForMap("SELECT * FROM booking_group WHERE id=? FOR UPDATE", groupId);
    }

    /** Guest bookings in seat order, each locked like a single booking's checkout. */
    List<Booking> lockMembers(long groupId) {
        Map<Long, Integer> seats = new java.util.TreeMap<>();
        jdbc.query("SELECT booking_id, position FROM booking_group_member WHERE group_id=?",
                rs -> { seats.put(rs.getLong(1), rs.getInt(2)); }, groupId);
        List<Booking> result = new ArrayList<>();
        for (Long id : seats.keySet()) result.add(bookings.findByIdForUpdate(id).orElseThrow()); // lock in id order
        result.sort(java.util.Comparator.comparing(b -> seats.get(b.getId())));
        return result;
    }

    private void requireRazorpay() {
        properties.requireCheckout();
        if (razorpay == null || !properties.isEnabled()) throw new IllegalStateException("Razorpay not configured");
    }

    /** Starts (or reuses) the group's order. The caller has verified the payment challenge. */
    @Transactional
    public PaymentInitResponse initiate(long groupId) {
        requireRazorpay();
        var group = lockGroup(groupId);
        if ("CAPTURED".equals(group.get("payment_status"))) throw new IllegalStateException("This group is already paid");
        var members = lockMembers(groupId);
        Booking lead = members.get(0);
        if (members.stream().noneMatch(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT && b.getPaymentMode() == PaymentMode.ONLINE))
            throw new IllegalStateException("Booking not in PENDING_PAYMENT: " + lead.getStatus());
        String currency = (String) group.get("currency");
        if (!properties.getCurrency().equalsIgnoreCase(currency))
            throw new IllegalStateException("Group bookings can be paid online only in " + properties.getCurrency());
        String order = (String) group.get("razorpay_order_id");
        int amount;
        if (order == null) {
            amount = members.stream().mapToInt(Booking::getRemainderCents).sum();
            if (amount <= 0) throw new IllegalStateException("Nothing to pay online for this group");
            try {
                JSONObject request = new JSONObject().put("amount", amount).put("currency", properties.getCurrency())
                        .put("receipt", "group-" + groupId).put("payment_capture", properties.isAutoCapture() ? 1 : 0);
                Order created = razorpay.orders.create(request);
                order = created.get("id");
            } catch (RazorpayException e) {
                throw new RuntimeException("Unable to create payment order", e);
            }
            jdbc.update("UPDATE booking_group SET razorpay_order_id=?, order_amount_cents=?, payment_status='INIT', updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                    order, amount, groupId);
        } else {
            amount = ((Number) group.get("order_amount_cents")).intValue();
        }
        return PaymentInitResponse.builder()
                .bookingId(lead.getId())
                .bookingReference(lead.getBookingReference())
                .razorpayKeyId(properties.getKeyId())
                .razorpayOrderId(order)
                .idempotencyKey("group-" + groupId)
                .amount(BigDecimal.valueOf(amount).movePointLeft(2))
                .currency(properties.getCurrency())
                .description(properties.getDescriptionPrefix() + " #" + lead.getBookingReference() + " · group of " + members.size())
                .customerName(lead.getUser().getName())
                .customerEmail(lead.getUser().getEmail())
                .customerPhone(lead.getUser().getPhone())
                .expiresAt(lead.getStartTs().minusMinutes(30))
                .build();
    }

    /** Browser hand-back from Razorpay Checkout: the signature is checked before anything is trusted. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirm(long groupId, RazorpayConfirmRequest request) {
        requireRazorpay();
        String order = (String) lockGroup(groupId).get("razorpay_order_id");
        if (order == null || !order.equals(request.getRazorpayOrderId()))
            throw new IllegalArgumentException("Order does not belong to this booking");
        try {
            JSONObject options = new JSONObject().put("razorpay_order_id", order)
                    .put("razorpay_payment_id", request.getRazorpayPaymentId())
                    .put("razorpay_signature", request.getRazorpaySignature());
            if (!Utils.verifyPaymentSignature(options, properties.getKeySecret()))
                throw new IllegalArgumentException("Invalid payment signature");
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to verify payment with Razorpay. Please retry.", e);
        }
        settle(groupId, request.getRazorpayPaymentId());
    }

    /** Webhook path. Returns false when the order is not a group's. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reconcileCaptured(String orderId, String paymentId) {
        var ids = jdbc.queryForList("SELECT id FROM booking_group WHERE razorpay_order_id=?", Long.class, orderId);
        if (ids.isEmpty()) return false;
        settle(ids.get(0), paymentId);
        return true;
    }

    /** Lifecycle job: true when the group's order has no capture, so its unpaid guest bookings may expire. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean confirmsUnpaid(Long bookingId) {
        Long groupId = groupOf(bookingId);
        if (groupId == null) return true;
        var group = jdbc.queryForMap("SELECT razorpay_order_id, payment_status FROM booking_group WHERE id=?", groupId);
        String order = (String) group.get("razorpay_order_id");
        if ("CAPTURED".equals(group.get("payment_status"))) return false;
        if (order == null) return true;
        if (razorpay == null || !properties.credentialsConfigured()) return false;
        try {
            for (com.razorpay.Payment remote : razorpay.orders.fetchPayments(order)) {
                if ("captured".equals(remote.get("status"))) {
                    settle(groupId, remote.get("id"));
                    return false;
                }
            }
            return true;
        } catch (RazorpayException | RuntimeException ex) {
            log.warn("Gateway check failed for group {}; keeping it pending: {}", groupId, ex.getClass().getSimpleName());
            return false;
        }
    }

    /** Pay at the spa instead: every guest booking still waiting for the online payment switches together. */
    @Transactional
    public PaymentService.CheckoutState payAtSpa(long groupId) {
        var group = lockGroup(groupId);
        var members = lockMembers(groupId);
        Booking lead = members.get(0);
        if ("CAPTURED".equals(group.get("payment_status"))) return new PaymentService.CheckoutState(lead.getStatus(), lead.getPaymentMode());
        String order = (String) group.get("razorpay_order_id");
        if (order != null) {
            requireRazorpay();
            try {
                for (com.razorpay.Payment attempt : razorpay.orders.fetchPayments(order)) {
                    String status = attempt.get("status");
                    if ("captured".equals(status)) {
                        settle(groupId, attempt.get("id"));
                        Booking fresh = bookings.findById(lead.getId()).orElseThrow();
                        return new PaymentService.CheckoutState(fresh.getStatus(), fresh.getPaymentMode());
                    }
                    if (!"failed".equals(status))
                        throw new IllegalStateException("Razorpay is still processing this payment. Please check again shortly.");
                }
            } catch (RazorpayException ex) {
                throw new IllegalStateException("Could not verify the online payment. Please try again shortly.", ex);
            }
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean pending = members.stream().anyMatch(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT);
        if (!pending) {
            if (lead.getStatus() == BookingStatus.CONFIRMED) return new PaymentService.CheckoutState(lead.getStatus(), lead.getPaymentMode());
            throw new IllegalStateException("This reservation is no longer pending. Check My bookings before booking again.");
        }
        if (!lead.getStartTs().isAfter(now) || !lead.getCreatedAt().plusMinutes(expiryMinutes).isAfter(now))
            throw new IllegalStateException("This reservation expired. Choose a new appointment time.");
        for (Booking booking : members) {
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) continue;
            booking.setPaymentMode(PaymentMode.OFFLINE);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setLastStatusChangedAt(now);
            bookings.save(booking);
            notifications.scheduleBookingNotifications(booking);
        }
        jdbc.update("UPDATE booking_group SET payment_mode='OFFLINE', updated_at=UTC_TIMESTAMP(6) WHERE id=?", groupId);
        return new PaymentService.CheckoutState(BookingStatus.CONFIRMED, PaymentMode.OFFLINE);
    }

    /** Records the captured group payment and splits it into one payment row per guest booking. */
    void settle(long groupId, String paymentId) {
        requireRazorpay();
        var group = lockGroup(groupId);
        String order = (String) group.get("razorpay_order_id");
        Number orderAmount = (Number) group.get("order_amount_cents");
        String recorded = (String) group.get("razorpay_payment_id");
        if (recorded != null) {
            if (recorded.equals(paymentId)) return; // already settled
            throw new IllegalArgumentException("Order already settled by another payment");
        }
        com.razorpay.Payment remote;
        try {
            remote = razorpay.payments.fetch(paymentId);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Unable to verify payment with Razorpay. Please retry.", e);
        }
        String currency = (String) group.get("currency");
        if (order == null || orderAmount == null || !paymentId.equals(remote.get("id")) || !order.equals(remote.get("order_id"))
                || ((Number) remote.get("amount")).longValue() != orderAmount.longValue()
                || !currency.equalsIgnoreCase(remote.get("currency")))
            throw new IllegalArgumentException("Gateway payment does not match the stored order, amount and currency");
        if (((Number) remote.get("amount_refunded")).longValue() != 0)
            throw new IllegalStateException("The full payment is required to confirm this appointment.");
        if (!"captured".equals(remote.get("status")) || !Boolean.TRUE.equals(remote.get("captured")))
            throw new IllegalStateException("Payment is not captured yet. Please retry verification shortly.");
        if (payments.findByTransactionId(paymentId).isPresent()) throw new IllegalArgumentException("Payment already used");

        var members = lockMembers(groupId);
        if (members.stream().mapToInt(Booking::getRemainderCents).sum() != orderAmount.intValue())
            throw new IllegalStateException("Group shares do not add up to the captured payment");
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE booking_group SET razorpay_payment_id=?, payment_status='CAPTURED', captured_at=?, updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                paymentId, Timestamp.valueOf(now.toLocalDateTime()), groupId);
        String method = remote.has("method") ? Objects.toString(remote.get("method"), null) : null;
        for (Booking booking : members) {
            Payment share = null;
            if (booking.getRemainderCents() > 0) {
                share = new Payment();
                share.setBooking(booking);
                share.setGateway("RAZORPAY");
                share.setIdempotencyKey("group-" + groupId + "-" + booking.getId());
                share.setAmountCents(booking.getRemainderCents());
                share.setTotalPrice(BigDecimal.valueOf(booking.getRemainderCents()).movePointLeft(2));
                share.setCurrency(properties.getCurrency());
                share.setPaymentStatus(PaymentStatus.CAPTURED);
                share.setCapturedAt(now);
                share.setPaymentMethod(method);
                share.setSharedGatewayPaymentId(paymentId);
                share.setMetaJson(new JSONObject().put("bookingGroupId", groupId).put("razorpayOrderId", order).toString());
                payments.save(share);
            }
            boolean lateAtSpa = booking.getPaymentMode() == PaymentMode.OFFLINE;
            if (lateAtSpa && booking.getStatus() == BookingStatus.CONFIRMED && booking.getStartTs().isAfter(now)) {
                booking.setPaymentMode(PaymentMode.ONLINE);
                lateAtSpa = false;
            }
            if (lateAtSpa || booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW) {
                // That guest's place was already released: their share goes to the refund workflow.
                if (share != null) refunds.lateCapture(booking, share);
                bookings.save(booking);
                continue;
            }
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setLastStatusChangedAt(now);
                bookings.save(booking);
                notifications.scheduleBookingNotifications(booking);
            }
            if (share != null) {
                var invoice = invoices.generateInvoiceForBooking(booking.getId());
                share.setReceiptUrl(invoice.pdfUrl());
                payments.save(share);
            }
        }
    }
}
