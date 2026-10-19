package com.nirvana.application;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.model.dto.UpiPaymentConfirmRequest;
import com.nirvana.application.model.enums.*;
import com.nirvana.application.repository.*;
import com.nirvana.application.security.*;
import com.nirvana.application.service.*;
import com.nirvana.application.service.impl.PaymentService;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSafetyTest {
    BookingRepository bookings;
    PaymentRepository payments;
    RazorpayClient gateway;
    PaymentService service;
    Booking booking;
    Payment payment;
    NotificationSchedulingService notifications;

    @BeforeEach void setup() throws Exception {
        bookings=mock(BookingRepository.class); payments=mock(PaymentRepository.class);
        gateway=new RazorpayClient("test_key", "test_secret");
        gateway.payments=mock(com.razorpay.PaymentClient.class);
        RazorpayProperties props=new RazorpayProperties();props.setEnabled(true);props.setKeySecret("test_secret");
        notifications=mock(NotificationSchedulingService.class);
        service=new PaymentService(gateway,props,bookings,payments,mock(InvoiceService.class),mock(NotificationService.class),mock(CurrencyConversionService.class),notifications,mock(PaymentMfaVerifier.class));
        User owner=User.builder().id(1L).build();
        booking=new Booking();booking.setId(10L);booking.setUser(owner);booking.setStatus(BookingStatus.PENDING_PAYMENT);booking.setPaymentMode(PaymentMode.ONLINE);booking.setRefundStatus(RefundStatus.NONE);
        payment=new Payment();payment.setId(20L);payment.setBooking(booking);payment.setGateway("RAZORPAY");payment.setIntentId("order_test");payment.setAmountCents(125000);payment.setCurrency("INR");payment.setPaymentStatus(PaymentStatus.INIT);payment.setReceiptUrl("existing-receipt");
        when(bookings.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));when(bookings.findById(10L)).thenReturn(Optional.of(booking));
        when(payments.findByIntentId("order_test")).thenReturn(Optional.of(payment));
        when(payments.findBookingIdByIntentId("order_test")).thenReturn(Optional.of(10L));
        signIn(1L);
        remote("captured",true,125000,"INR","order_test");
    }
    void signIn(long id) {
        var principal=new UserPrincipal(id,"customer","customer@test.invalid","",true,List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    void remote(String status,boolean captured,int amount,String currency,String order) throws Exception {
        when(gateway.payments.fetch("pay_test")).thenReturn(new com.razorpay.Payment(new JSONObject().put("id","pay_test").put("order_id",order).put("amount",amount).put("currency",currency).put("status",status).put("captured",captured)));
    }
    RazorpayConfirmRequest signed() throws Exception {
        var request=new RazorpayConfirmRequest();request.setRazorpayOrderId("order_test");request.setRazorpayPaymentId("pay_test");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test_secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        request.setRazorpaySignature(HexFormat.of().formatHex(mac.doFinal("order_test|pay_test".getBytes(StandardCharsets.UTF_8))));return request;
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void anotherCustomerCannotReadReceipt(){signIn(2);assertThrows(AccessDeniedException.class,()->service.fetchReceiptUrl(10L));verifyNoInteractions(payments);}
    @Test void anotherCustomerCannotReadTimeline(){signIn(2);assertThrows(AccessDeniedException.class,()->service.getPaymentTimelineForBooking(10L));}
    @Test void anotherCustomerCannotCreateOrder(){signIn(2);assertThrows(AccessDeniedException.class,()->service.initiateRazorpayPayment(10L,"key"));}
    @Test void anotherCustomerCannotConfirm() throws Exception {signIn(2);var r=signed();assertThrows(AccessDeniedException.class,()->service.confirmRazorpayPayment(10L,r));verify(gateway.payments,never()).fetch(anyString());}
    @Test void anotherCustomerCannotRefund(){signIn(2);assertThrows(AccessDeniedException.class,()->service.refundBookingPayment(10L,100,"test"));}
    @Test void directUpiNeverConfirms(){assertThrows(IllegalStateException.class,()->service.confirmUpiPayment(10L,new UpiPaymentConfirmRequest()));assertEquals(BookingStatus.PENDING_PAYMENT,booking.getStatus());verifyNoInteractions(payments);}
    @Test void invalidSignatureCannotConfirm() throws Exception {var r=signed();r.setRazorpaySignature("bad");assertThrows(Exception.class,()->service.confirmRazorpayPayment(10L,r));assertEquals(PaymentStatus.INIT,payment.getPaymentStatus());verify(gateway.payments,never()).fetch(anyString());}
    @Test void authorizedButUncapturedCannotConfirm() throws Exception {remote("authorized",false,125000,"INR","order_test");var r=signed();assertThrows(IllegalStateException.class,()->service.confirmRazorpayPayment(10L,r));assertEquals(BookingStatus.PENDING_PAYMENT,booking.getStatus());}
    @Test void amountMismatchCannotConfirm() throws Exception {remote("captured",true,1,"INR","order_test");var r=signed();assertThrows(IllegalArgumentException.class,()->service.confirmRazorpayPayment(10L,r));assertEquals(PaymentStatus.INIT,payment.getPaymentStatus());}
    @Test void currencyMismatchCannotConfirm() throws Exception {remote("captured",true,125000,"USD","order_test");var r=signed();assertThrows(IllegalArgumentException.class,()->service.confirmRazorpayPayment(10L,r));}
    @Test void orderMismatchCannotConfirm() throws Exception {remote("captured",true,125000,"INR","order_other");var r=signed();assertThrows(IllegalArgumentException.class,()->service.confirmRazorpayPayment(10L,r));}
    @Test void verifiedCaptureConfirmsExactlyOnce() throws Exception {service.confirmRazorpayPayment(10L,signed());service.confirmRazorpayPayment(10L,signed());assertEquals(PaymentStatus.CAPTURED,payment.getPaymentStatus());assertEquals(BookingStatus.CONFIRMED,booking.getStatus());verify(notifications,times(1)).scheduleBookingNotifications(booking);}
    @Test void lateCaptureDoesNotReopenCancelledBooking() throws Exception {booking.setStatus(BookingStatus.CANCELLED);service.confirmRazorpayPayment(10L,signed());assertEquals(BookingStatus.CANCELLED,booking.getStatus());assertEquals(RefundStatus.REQUESTED,booking.getRefundStatus());assertEquals(PaymentStatus.CAPTURED,payment.getPaymentStatus());verifyNoInteractions(notifications);}
    @Test void signedWebhookPathAlsoConfirmsBooking(){SecurityContextHolder.clearContext();service.reconcileCapturedPayment("order_test","pay_test");assertEquals(BookingStatus.CONFIRMED,booking.getStatus());}
    @Test void cannotReplayAnotherOrdersIdempotencyKey(){booking.setRemainderCents(125000);Payment other=new Payment();Booking b=new Booking();b.setId(99L);other.setBooking(b);other.setGateway("RAZORPAY");when(payments.findByIdempotencyKey("reused")).thenReturn(Optional.of(other));assertThrows(IllegalArgumentException.class,()->service.initiateRazorpayPayment(10L,"reused"));}
}
