package com.nirvana.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.*;
import com.nirvana.application.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** A group guest's payment row is a share of one provider payment that other guests also hold. */
class GroupShareRefundTest {
    final BookingRepository bookings = mock(BookingRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final PaymentRefundRepository cases = mock(PaymentRefundRepository.class);
    final RazorpayRefundGateway gateway = mock(RazorpayRefundGateway.class);
    final BookingRefundRecovery recovery = new BookingRefundRecovery(bookings, cases, payments, gateway, mock(LoyaltyService.class));

    static Payment share(long id, Booking booking, int amount) {
        Payment p = new Payment();
        p.setId(id);
        p.setBooking(booking);
        p.setAmountCents(amount);
        p.setCurrency("INR");
        p.setGateway("RAZORPAY");
        p.setPaymentStatus(PaymentStatus.CAPTURED);
        p.setSharedGatewayPaymentId("pay_group");
        return p;
    }

    @Test void refundingOneGuestPostsOnlyTheirShareAgainstTheSharedPayment() {
        Booking guest = new Booking();
        guest.setId(8L);
        guest.setUser(User.builder().id(3L).build());
        guest.setStatus(BookingStatus.CANCELLED);
        Payment mine = share(20L, guest, 9440);
        when(cases.findTopByBookingIdOrderByCreatedAtDesc(8L)).thenReturn(Optional.empty());
        recovery.enqueue(guest, mine, 9440, "User cancelled booking");
        verify(cases).save(argThat(r -> r.getAmountCents() == 9440 && r.getPayment() == mine));
        assertThrows(IllegalArgumentException.class, () -> recovery.enqueue(guest, mine, 9441, "more than the share"));

        PaymentRefund refund = PaymentRefund.builder().id(30L).booking(guest).payment(mine).amountCents(9440)
                .status(RefundRequestStatus.PROCESSING).currency("INR").build();
        when(cases.findBookingId(30L)).thenReturn(Optional.of(8L));
        when(cases.findById(30L)).thenReturn(Optional.of(refund));
        when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(guest));
        // The provider payment already carries another guest's refund; it must not be counted here.
        when(gateway.submit(refund)).thenReturn(new ObjectMapper().createObjectNode().put("id", "rfnd_1").put("payment_id", "pay_group")
                .put("currency", "INR").put("amount", 9440).put("status", "processed"));
        recovery.dispatch(30L);
        assertEquals(9440, mine.getRefundedCents());
        assertEquals(PaymentStatus.REFUNDED, mine.getPaymentStatus());
        assertEquals(RefundRequestStatus.COMPLETED, refund.getStatus());
        verify(gateway, never()).processedAmount(any(), anyString());
        assertEquals("pay_group", mine.gatewayPaymentId());
        assertTrue(mine.sharedCapture());
    }
}
