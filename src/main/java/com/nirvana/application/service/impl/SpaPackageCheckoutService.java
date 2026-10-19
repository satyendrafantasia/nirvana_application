package com.nirvana.application.service.impl;
import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.User;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.model.enums.spa.*;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.repository.*;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import jakarta.persistence.*;
import org.json.JSONObject;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class SpaPackageCheckoutService {
 private final SpaPackageRepository packages;private final UserSpaPackageSubscriptionRepository subscriptions;
 private final EntityManager em;private final RazorpayProperties props;private final RazorpayClient gateway;
 public SpaPackageCheckoutService(SpaPackageRepository p,UserSpaPackageSubscriptionRepository s,EntityManager e,RazorpayProperties props,@Nullable RazorpayClient g){packages=p;subscriptions=s;em=e;this.props=props;gateway=g;}
 private void enabled(){if(!props.isEnabled()||gateway==null)throw new IllegalStateException("Online package payments are not available yet. Please contact Nirvana.");}
 private int amount(UserSpaPackageSubscription s){return s.getPricePaid().movePointRight(2).intValueExact();}
 private PackageCheckoutService.Purchase view(UserSpaPackageSubscription s){return new PackageCheckoutService.Purchase(s.getId(),s.getLevel().name(),s.getStatus().name(),s.getPaymentStatus().name(),s.getRemainingSessions(),s.getTotalSessions(),amount(s),s.getCurrency(),s.getExpiryDate(),s.getSpaPackage().getId());}
 private PackageCheckoutService.Checkout intent(UserSpaPackageSubscription s){return new PackageCheckoutService.Checkout(view(s),props.getKeyId(),s.getRazorpayOrderId(),amount(s),s.getCurrency());}
 private UserSpaPackageSubscription owned(Long userId,Long id){var s=subscriptions.findForUpdate(id).orElseThrow(()->new EntityNotFoundException("Purchase not found"));if(!s.getUser().getId().equals(userId))throw new EntityNotFoundException("Purchase not found");return s;}
 @Transactional public PackageCheckoutService.Checkout start(Long userId,Long spaId,Long packageId){
  enabled();var user=em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);if(user==null)throw new EntityNotFoundException("User not found");
  for(var s:subscriptions.findByUserIdOrderByPurchaseDateDesc(userId))if(s.getSpa().getId().equals(spaId)){
   if(s.getStatus()==UserSpaPackageStatus.PAYMENT_PENDING){if(!s.getSpaPackage().getId().equals(packageId))throw new IllegalStateException("Resume your existing purchase for this spa first.");return resumeInternal(s);}
   if(s.getStatus()==UserSpaPackageStatus.ACTIVE&&s.getRemainingSessions()>0&&(s.getExpiryDate()==null||s.getExpiryDate().isAfter(OffsetDateTime.now())))throw new IllegalStateException("Use your active package for this spa before buying another.");
   if(s.getStatus()==UserSpaPackageStatus.ACTIVE){s.setStatus(s.getRemainingSessions()<=0?UserSpaPackageStatus.EXHAUSTED:UserSpaPackageStatus.EXPIRED);subscriptions.save(s);}
  }
  var p=packages.findBySpaIdAndId(spaId,packageId).filter(x->x.getStatus()==SpaPackageStatus.ACTIVE).orElseThrow(()->new EntityNotFoundException("Package unavailable"));
  var s=UserSpaPackageSubscription.builder().user(user).spa(p.getSpa()).spaPackage(p).level(p.getLevel()).pricePaid(p.getPrice()).totalSessions(p.getFreeSessionsCount()).remainingSessions(0).purchaseDate(OffsetDateTime.now()).status(UserSpaPackageStatus.PAYMENT_PENDING).paymentStatus(PaymentStatus.PENDING).currency(p.getSpa().getDefaultCurrency()).build();
  if(amount(s)<=0||s.getTotalSessions()<=0)throw new IllegalStateException("Package pricing is not configured");
  subscriptions.saveAndFlush(s);
  try{s.setRazorpayOrderId(gateway.orders.create(new JSONObject().put("amount",amount(s)).put("currency",s.getCurrency()).put("receipt","spa-package-"+s.getId())).get("id"));}catch(Exception e){throw new IllegalStateException("Unable to start checkout",e);}
  return intent(s);
 }
 @Transactional public PackageCheckoutService.Checkout resume(Long userId,Long id){enabled();return resumeInternal(owned(userId,id));}
 private PackageCheckoutService.Checkout resumeInternal(UserSpaPackageSubscription s){
  if(s.getStatus()==UserSpaPackageStatus.PAYMENT_PENDING)try{for(com.razorpay.Payment p:gateway.orders.fetchPayments(s.getRazorpayOrderId()))if("captured".equals(p.get("status"))){settle(s,p.get("id"));break;}}catch(Exception e){throw new IllegalStateException("Unable to verify payment. Retry this purchase before paying again.",e);}
  return intent(s);
 }
 @Transactional public PackageCheckoutService.Purchase confirm(Long userId,Long id,RazorpayConfirmRequest request){
  enabled();var s=owned(userId,id);
  if(!Objects.equals(s.getRazorpayOrderId(),request.getRazorpayOrderId()))throw new IllegalArgumentException("Order mismatch");
  try{if(!Utils.verifyPaymentSignature(new JSONObject().put("razorpay_order_id",s.getRazorpayOrderId()).put("razorpay_payment_id",request.getRazorpayPaymentId()).put("razorpay_signature",request.getRazorpaySignature()),props.getKeySecret()))throw new IllegalArgumentException("Invalid signature");settle(s,request.getRazorpayPaymentId());return view(s);}catch(com.razorpay.RazorpayException e){throw new IllegalStateException("Payment verification unavailable",e);}
 }
 private void settle(UserSpaPackageSubscription s,String id)throws com.razorpay.RazorpayException{
  com.razorpay.Payment p=gateway.payments.fetch(id);
  if(!Objects.equals(id,p.get("id"))||!Objects.equals(s.getRazorpayOrderId(),p.get("order_id"))||!"captured".equals(p.get("status"))||!Boolean.TRUE.equals(p.get("captured"))||((Number)p.get("amount")).intValue()!=amount(s)||!Objects.equals(s.getCurrency(),p.get("currency"))||((Number)p.get("amount_refunded")).intValue()!=0)throw new IllegalStateException("Payment has not been captured for this package and amount.");
  if(s.getStatus()!=UserSpaPackageStatus.PAYMENT_PENDING)return;
  s.setPaymentReferenceId(id);s.setPaymentStatus(PaymentStatus.SUCCESS);s.setStatus(UserSpaPackageStatus.ACTIVE);s.setRemainingSessions(s.getTotalSessions());subscriptions.save(s);
 }
 @Transactional public void captured(String orderId,String paymentId){subscriptions.findIdByOrder(orderId).ifPresent(id->{var s=subscriptions.findForUpdate(id).orElseThrow();try{enabled();settle(s,paymentId);}catch(com.razorpay.RazorpayException e){throw new IllegalStateException("Unable to reconcile spa package capture",e);}});}
 @Transactional public void refunded(String paymentId){if(gateway==null)return;try{com.razorpay.Payment p=gateway.payments.fetch(paymentId);subscriptions.findIdByOrder(p.get("order_id")).ifPresent(id->{var s=subscriptions.findForUpdate(id).orElseThrow();if(Objects.equals(paymentId,s.getPaymentReferenceId())&&((Number)p.get("amount_refunded")).intValue()>0){s.setStatus(UserSpaPackageStatus.CANCELLED);s.setRemainingSessions(0);subscriptions.save(s);}});}catch(com.razorpay.RazorpayException e){throw new IllegalStateException("Unable to reconcile spa package refund",e);}}
}
