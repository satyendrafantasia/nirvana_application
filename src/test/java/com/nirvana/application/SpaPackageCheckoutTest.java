package com.nirvana.application;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.spa.*;
import com.nirvana.application.model.spa.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.repository.*;
import com.nirvana.application.service.impl.*;
import com.razorpay.RazorpayClient;
import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpaPackageCheckoutTest {
 UserSpaPackageSubscriptionRepository subscriptions;SpaPackageRepository packages;EntityManager em;
 RazorpayClient gateway;SpaPackageCheckoutService service;UserSpaPackageSubscription subscription;JSONObject remote;RazorpayConfirmRequest request;
 @BeforeEach void setup() throws Exception {
  subscriptions=mock(UserSpaPackageSubscriptionRepository.class);packages=mock(SpaPackageRepository.class);em=mock(EntityManager.class);
  gateway=new RazorpayClient("key","secret");gateway.payments=mock(com.razorpay.PaymentClient.class);gateway.orders=mock(com.razorpay.OrderClient.class);
  var props=new RazorpayProperties();props.setEnabled(true);props.setKeySecret("secret");props.setKeyId("key");
  service=new SpaPackageCheckoutService(packages,subscriptions,em,props,gateway);
  var p=SpaPackage.builder().id(1L).level(SpaPackageLevel.SILVER).build();
  subscription=UserSpaPackageSubscription.builder().id(9L).user(User.builder().id(2L).build()).spaPackage(p).level(p.getLevel())
   .status(UserSpaPackageStatus.PAYMENT_PENDING).paymentStatus(PaymentStatus.PENDING).remainingSessions(0).pricePaid(new java.math.BigDecimal("200.00")).currency("INR")
   .totalSessions(5).razorpayOrderId("order_1").build();
  when(subscriptions.findForUpdate(9L)).thenReturn(Optional.of(subscription));
  remote=new JSONObject().put("id","pay_1").put("order_id","order_1").put("amount",20000).put("currency","INR").put("status","captured").put("captured",true).put("amount_refunded",0);
  when(gateway.payments.fetch("pay_1")).thenAnswer(i->new com.razorpay.Payment(remote));
  request=new RazorpayConfirmRequest();request.setRazorpayOrderId("order_1");request.setRazorpayPaymentId("pay_1");
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  request.setRazorpaySignature(HexFormat.of().formatHex(mac.doFinal("order_1|pay_1".getBytes(StandardCharsets.UTF_8))));
 }
 @Test void activatesOnlyVerifiedCaptureAndReplayDoesNotRestoreUsedSessions(){
  assertEquals("ACTIVE",service.confirm(2L,9L,request).status());assertEquals(5,subscription.getRemainingSessions());
  subscription.setRemainingSessions(3);service.confirm(2L,9L,request);assertEquals(3,subscription.getRemainingSessions());
 }
 @Test void anotherCustomerCannotConfirm(){assertThrows(jakarta.persistence.EntityNotFoundException.class,()->service.confirm(8L,9L,request));verifyNoInteractions(gateway.payments);}
 @Test void wrongSignatureCannotActivate(){request.setRazorpaySignature("bad");assertThrows(Exception.class,()->service.confirm(2L,9L,request));assertEquals(0,subscription.getRemainingSessions());}
 @Test void wrongOrderCannotActivate(){request.setRazorpayOrderId("other");assertThrows(IllegalArgumentException.class,()->service.confirm(2L,9L,request));}
 @Test void authorizedPaymentCannotActivate(){remote.put("status","authorized").put("captured",false);assertThrows(IllegalStateException.class,()->service.confirm(2L,9L,request));}
 @Test void wrongAmountCannotActivate(){remote.put("amount",1);assertThrows(IllegalStateException.class,()->service.confirm(2L,9L,request));}
 @Test void wrongCurrencyCannotActivate(){remote.put("currency","USD");assertThrows(IllegalStateException.class,()->service.confirm(2L,9L,request));}
 @Test void refundedPaymentCannotActivate(){remote.put("amount_refunded",20000);assertThrows(IllegalStateException.class,()->service.confirm(2L,9L,request));}
 @Test void resumeRecoversCapturedPaymentWithoutNewOrder() throws Exception {
  when(gateway.orders.fetchPayments("order_1")).thenReturn(List.of(new com.razorpay.Payment(remote)));
  assertEquals("ACTIVE",service.resume(2L,9L).purchase().status());verify(gateway.orders,never()).create(any());
 }
}
