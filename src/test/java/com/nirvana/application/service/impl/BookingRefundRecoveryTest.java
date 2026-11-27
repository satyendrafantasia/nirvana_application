package com.nirvana.application.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.*;
import com.nirvana.application.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookingRefundRecoveryTest {
    final BookingRepository bookings = mock(BookingRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final PaymentRefundRepository cases = mock(PaymentRefundRepository.class);
    final RazorpayRefundGateway gateway = mock(RazorpayRefundGateway.class);
    final LoyaltyService loyalty = mock(LoyaltyService.class);
    final BookingRefundRecovery recovery = new BookingRefundRecovery(bookings, cases, payments, gateway, loyalty);
    Booking booking;
    Payment payment;
    PaymentRefund refund;

    @BeforeEach void setUp() {
        booking = new Booking();
        booking.setId(4L);
        booking.setUser(User.builder().id(2L).build());
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRefundStatus(RefundStatus.PROCESSING);
        payment = new Payment();
        payment.setId(5L);
        payment.setBooking(booking);
        payment.setAmountCents(5000);
        payment.setGateway("RAZORPAY");
        payment.setPaymentStatus(PaymentStatus.CAPTURED);
        payment.setTransactionId("pay_abc");
        refund = PaymentRefund.builder().id(6L).booking(booking).payment(payment).amountCents(2000)
                .status(RefundRequestStatus.PROCESSING).currency("INR").build();
        when(bookings.findByIdForUpdate(4L)).thenReturn(Optional.of(booking));
        when(cases.findBookingId(6L)).thenReturn(Optional.of(4L));
        when(cases.findById(6L)).thenReturn(Optional.of(refund));
        when(payments.findBookingIdByTransactionId("pay_abc")).thenReturn(Optional.of(4L));
        when(payments.findByTransactionId("pay_abc")).thenReturn(Optional.of(payment));
    }

    static JsonNode response(String status) {
        return new ObjectMapper().createObjectNode().put("id", "rfnd_abc").put("payment_id", "pay_abc")
                .put("currency", "INR").put("amount", 2000).put("status", status).put("receipt","nirvana-6-1");
    }

    @Test void lateCaptureCreatesOneReviewCaseWithoutReopeningOrPaying() {
        when(cases.findTopByBookingIdOrderByCreatedAtDesc(4L)).thenReturn(Optional.empty()).thenReturn(Optional.of(refund));
        recovery.lateCapture(booking, payment);
        recovery.lateCapture(booking, payment);
        verify(cases, times(1)).save(argThat(r -> r.getStatus() == RefundRequestStatus.REQUESTED && r.getAmountCents() == 5000));
        assertEquals(RefundStatus.REQUESTED, booking.getRefundStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verifyNoInteractions(gateway);
    }

    @Test void pendingRefundPostsNothingAndVerifiedWebhookCompletesOnlyOnce() {
        when(gateway.submit(refund)).thenReturn(response("pending"));
        recovery.dispatch(6L);
        assertEquals("rfnd_abc", refund.getGatewayRefundId());
        assertEquals(RefundRequestStatus.PROCESSING, refund.getStatus());
        assertEquals(0, payment.getRefundedCents());
        when(cases.findByGatewayRefundId("rfnd_abc")).thenReturn(Optional.of(refund));
        when(gateway.fetch("pay_abc", "rfnd_abc")).thenReturn(response("processed"));
        when(gateway.processedAmount(payment, "rfnd_abc")).thenReturn(2000);
        recovery.webhook("pay_abc","rfnd_abc");
        recovery.webhook("pay_abc","rfnd_abc");
        assertEquals(RefundRequestStatus.COMPLETED, refund.getStatus());
        assertEquals(RefundStatus.COMPLETED, booking.getRefundStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getPaymentStatus());
        assertEquals(2000, payment.getRefundedCents());
        verify(loyalty, times(1)).syncEarnedForBooking(4L);
    }

    @Test void failedRefundIsActionableWithoutPostingMoneyOrAutomaticResubmission() {
        when(gateway.submit(refund)).thenReturn(response("failed"));
        recovery.dispatch(6L);
        recovery.dispatch(6L);
        assertEquals(RefundRequestStatus.FAILED, refund.getStatus());
        assertEquals(RefundStatus.FAILED, booking.getRefundStatus());
        assertEquals(0, payment.getRefundedCents());
        verify(gateway, times(1)).submit(refund);
        verifyNoInteractions(loyalty);
    }

    @Test void rejectsWrongProviderAmountAndAcceptsWebhookBeforeSubmissionResponse() {
        var wrong = new ObjectMapper().valueToTree(response("processed"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrong).put("amount", 100);
        when(gateway.submit(refund)).thenReturn(wrong);
        assertThrows(IllegalStateException.class, () -> recovery.dispatch(6L));
        assertNull(refund.getGatewayRefundId());
        when(cases.findTopByBookingIdOrderByCreatedAtDesc(4L)).thenReturn(Optional.of(refund));
        when(gateway.fetch("pay_abc", "rfnd_abc")).thenReturn(response("pending"));
        recovery.webhook("pay_abc","rfnd_abc");
        assertEquals("rfnd_abc",refund.getGatewayRefundId());
        assertEquals(0,payment.getRefundedCents());
    }

    @Test void unresolvedSubmissionKeepsItsOriginalIdempotencyAttemptWhenStaffResumeIt() {
        recovery.defer(6L, true);
        assertEquals(RefundRequestStatus.REVIEW_REQUIRED, refund.getStatus());
        recovery.resume(6L);
        assertEquals(RefundRequestStatus.PROCESSING, refund.getStatus());
        assertEquals(1, refund.getGatewayAttempt());
        assertNull(refund.getGatewayRefundId());
        verifyNoInteractions(gateway);
    }

    @Test void historicalRecoveryChecksProviderBeforeSendingAnotherRefund() {
        refund.setHistoricalPosting(true);
        refund.setGatewayAttempt(0);
        refund.setStatus(RefundRequestStatus.REVIEW_REQUIRED);
        doThrow(new IllegalStateException("existing provider refund")).when(gateway).verifyNeverRefunded(payment);
        assertThrows(IllegalStateException.class, () -> recovery.recoverMissingHistorical(6L));
        assertEquals(RefundRequestStatus.REVIEW_REQUIRED, refund.getStatus());
        assertEquals(0, refund.getGatewayAttempt());
        doNothing().when(gateway).verifyNeverRefunded(payment);
        recovery.recoverMissingHistorical(6L);
        assertEquals(RefundRequestStatus.PROCESSING, refund.getStatus());
        assertEquals(1, refund.getGatewayAttempt());
        assertThrows(IllegalStateException.class, () -> recovery.recoverMissingHistorical(6L));
        verify(gateway, never()).submit(any());
    }

    @Test void missingWebhookDisablesCheckoutAndRepeatedRefundsUseTheSameKeyAndBody() {
        var props = new RazorpayProperties();
        props.setEnabled(true);
        props.setKeyId("test-id");
        props.setKeySecret("test-secret");
        assertFalse(props.readyForCheckout());
        assertTrue(props.credentialsConfigured());
        assertThrows(IllegalStateException.class, props::requireCheckout);
        props.setWebhookSecret("test-webhook");
        assertTrue(props.readyForCheckout());
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new RazorpayRefundGateway(props, builder);
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("https://api.razorpay.com/v1/payments/pay_abc/refund")).andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Refund-Idempotency","pay_abc-nirvana-6-1"))
                    .andExpect(jsonPath("$.amount").value(2000)).andExpect(jsonPath("$.receipt").value("nirvana-6-1"))
                    .andRespond(i == 0 ? withServerError() : withSuccess(response("pending").toString(),MediaType.APPLICATION_JSON));
        }
        assertThrows(org.springframework.web.client.RestClientException.class, () -> adapter.submit(refund));
        assertEquals("pending", adapter.submit(refund).path("status").asText());
        server.verify();
    }
}
