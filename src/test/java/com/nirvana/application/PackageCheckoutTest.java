package com.nirvana.application;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.*;
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

class PackageCheckoutTest {
 UserPackageSubscriptionRepository subscriptions;ServicePackageRepository packages;EntityManager em;
 RazorpayClient gateway;PackageCheckoutService service;UserPackageSubscription subscription;JSONObject remote;RazorpayConfirmRequest request;
 @BeforeEach void setup() throws Exception {
  subscriptions=mock(UserPackageSubscriptionRepository.class);packages=mock(ServicePackageRepository.class);em=mock(EntityManager.class);
  gateway=new RazorpayClient("key","secret");gateway.payments=mock(com.razorpay.PaymentClient.class);gateway.orders=mock(com.razorpay.OrderClient.class);
  var props=new RazorpayProperties();props.setEnabled(true);props.setKeySecret("secret");props.setKeyId("key");
  service=new PackageCheckoutService(packages,subscriptions,em,props,gateway);
  var p=ServicePackage.builder().id(1L).packageType(PackageType.GLOBAL_BRONZE).sessionCount(5).build();
  subscription=UserPackageSubscription.builder().id(9L).user(User.builder().id(2L).build()).servicePackage(p).packageType(p.getPackageType())
   .status(PackageStatus.PAYMENT_PENDING).paymentStatus(PaymentStatus.PENDING).remainingSessions(0).amountCents(20000).currency("INR")
   .purchasedSessions(5).purchasedValidityDays(180).razorpayOrderId("order_1").build();
  when(subscriptions.findOwnedForUpdate(9L,2L)).thenReturn(Optional.of(subscription));
  remote=new JSONObject().put("id","pay_1").put("order_id","order_1").put("amount",20000).put("currency","INR").put("status","captured").put("captured",true).put("amount_refunded",0);
  when(gateway.payments.fetch("pay_1")).thenAnswer(i->new com.razorpay.Payment(remote));
  request=new RazorpayConfirmRequest();request.setRazorpayOrderId("order_1");request.setRazorpayPaymentId("pay_1");
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  request.setRazorpaySignature(HexFormat.of().formatHex(mac.doFinal("order_1|pay_1".getBytes(StandardCharsets.UTF_8))));
 }
 @Test void activatesOnlyVerifiedCaptureAndReplayDoesNotRestoreUsedSessions(){
  assertEquals("ACTIVE",service.confirm(2L,9L,request).status());assertEquals(5,subscription.getRemainingSessions());assertNotNull(subscription.getExpiryDate());
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
 @Test void legacyClientSuccessIsRejected(){
  var old=new PackageSubscriptionServiceImpl(packages,subscriptions,mock(UserPackageUsageLogRepository.class),mock(UserRepository.class),mock(BookingRepository.class));
  var r=new CreatePackagePurchaseRequest();r.setPaymentStatus(PaymentStatus.CAPTURED);
  assertThrows(IllegalStateException.class,()->old.purchasePackage(2L,r));verify(subscriptions,never()).save(any());
 }
}
