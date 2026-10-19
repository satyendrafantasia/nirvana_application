package com.nirvana.application.service.impl;

import com.nirvana.application.config.RazorpayProperties;
import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.*;
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
import java.util.*;

@Service
public class PackageCheckoutService {
 private final ServicePackageRepository packages;
 private final UserPackageSubscriptionRepository subscriptions;
 private final EntityManager em;
 private final RazorpayProperties props;
 private final RazorpayClient gateway;
 public PackageCheckoutService(ServicePackageRepository packages, UserPackageSubscriptionRepository subscriptions,
   EntityManager em, RazorpayProperties props, @Nullable RazorpayClient gateway) {
  this.packages=packages; this.subscriptions=subscriptions; this.em=em; this.props=props; this.gateway=gateway;
 }
 public record SpaItem(Long id,String name) {}
 public record CatalogItem(Long id,String packageType,String description,int amountCents,String currency,
   int sessions,Integer validityDays,List<SpaItem> eligibleSpas) {}
 public record Purchase(Long subscriptionId,String packageType,String status,String paymentStatus,int remainingSessions,
   Integer sessions,Integer amountCents,String currency,OffsetDateTime expiryDate,Long packageId) {}
 public record Checkout(Purchase purchase,String razorpayKeyId,String razorpayOrderId,Integer amountCents,String currency) {}
 @Transactional(readOnly=true)
 public List<CatalogItem> catalog() {
  return packages.findAll().stream().filter(p->Boolean.TRUE.equals(p.getActive())).map(p->new CatalogItem(
   p.getId(),p.getPackageType().name(),p.getDescription(),p.getPriceCents(),props.getCurrency(),p.getSessionCount(),
   p.getValidityDays(),p.getEligibleSpas().stream().map(s->new SpaItem(s.getId(),s.getName())).toList())).toList();
 }
 @Transactional(readOnly=true)
 public List<Purchase> mine(Long userId) {
  return subscriptions.findByUserIdOrderByPurchaseDateDesc(userId).stream().map(this::view).toList();
 }
 private Purchase view(UserPackageSubscription s) {
  String status=s.getStatus().name();
  if(s.getStatus()==PackageStatus.ACTIVE && s.getExpiryDate()!=null && s.getExpiryDate().isBefore(OffsetDateTime.now())) status="EXPIRED";
  return new Purchase(s.getId(),s.getPackageType().name(),status,s.getPaymentStatus().name(),s.getRemainingSessions(),
   s.getPurchasedSessions()!=null?s.getPurchasedSessions():s.getServicePackage().getSessionCount(),s.getAmountCents(),s.getCurrency(),s.getExpiryDate(),s.getServicePackage().getId());
 }
 private void enabled() {
  if(!props.isEnabled() || gateway==null) throw new IllegalStateException("Online package payments are not available yet. Please contact Nirvana.");
 }
 @Transactional
 public Checkout start(Long userId,Long packageId) {
  enabled();
  User user=em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);
  if(user==null) throw new EntityNotFoundException("User not found");
  for(UserPackageSubscription s:subscriptions.findByUserIdOrderByPurchaseDateDesc(userId)) {
   if(s.getStatus()==PackageStatus.PAYMENT_PENDING) {
    if(!s.getServicePackage().getId().equals(packageId)) throw new IllegalStateException("Resume your existing package checkout in My Packages before buying another package.");
    return resumeInternal(s);
   }
   if(s.getStatus()==PackageStatus.ACTIVE && s.getRemainingSessions()>0 && (s.getExpiryDate()==null || s.getExpiryDate().isAfter(OffsetDateTime.now())))
    throw new IllegalStateException("You already have an active package. Use its sessions before purchasing another.");
   if(s.getStatus()==PackageStatus.ACTIVE) {s.setStatus(s.getRemainingSessions()<=0?PackageStatus.EXHAUSTED:PackageStatus.EXPIRED);subscriptions.save(s);}
  }
  ServicePackage p=packages.findById(packageId).filter(x->Boolean.TRUE.equals(x.getActive())).orElseThrow(()->new EntityNotFoundException("Package is unavailable"));
  if(p.getPriceCents()<=0 || p.getSessionCount()<=0) throw new IllegalStateException("Package pricing is not configured");
  if(p.getEligibleSpas().isEmpty()) throw new IllegalStateException("This package does not have any eligible spas yet.");
  UserPackageSubscription s=new UserPackageSubscription();
  s.setUser(user);s.setServicePackage(p);s.setPackageType(p.getPackageType());s.setPurchaseDate(OffsetDateTime.now());
  s.setStatus(PackageStatus.PAYMENT_PENDING);s.setPaymentStatus(PaymentStatus.PENDING);s.setRemainingSessions(0);
  s.setAmountCents(p.getPriceCents());s.setCurrency(props.getCurrency());s.setPurchasedSessions(p.getSessionCount());s.setPurchasedValidityDays(p.getValidityDays());
  subscriptions.saveAndFlush(s);
  try {
   JSONObject order=new JSONObject().put("amount",s.getAmountCents()).put("currency",s.getCurrency()).put("receipt","package-"+s.getId());
   s.setRazorpayOrderId(gateway.orders.create(order).get("id"));
  } catch(Exception e) {throw new IllegalStateException("Unable to start package checkout. Please retry.",e);}
  return intent(s);
 }
 private Checkout intent(UserPackageSubscription s) {return new Checkout(view(s),props.getKeyId(),s.getRazorpayOrderId(),s.getAmountCents(),s.getCurrency());}
 private UserPackageSubscription owned(Long userId,Long id) {return subscriptions.findOwnedForUpdate(id,userId).orElseThrow(()->new EntityNotFoundException("Package purchase not found"));}
 @Transactional
 public Checkout resume(Long userId,Long id) {enabled();return resumeInternal(owned(userId,id));}
 private Checkout resumeInternal(UserPackageSubscription s) {
  if(s.getStatus()==PackageStatus.PAYMENT_PENDING) try {
   for(com.razorpay.Payment p:gateway.orders.fetchPayments(s.getRazorpayOrderId())) if("captured".equals(p.get("status"))) {settle(s,p.get("id"));break;}
  } catch(Exception e) {throw new IllegalStateException("Unable to verify payment. Retry this purchase before paying again.",e);}
  return intent(s);
 }
 @Transactional
 public Purchase confirm(Long userId,Long id,RazorpayConfirmRequest request) {
  enabled(); UserPackageSubscription s=owned(userId,id);
  if(!Objects.equals(s.getRazorpayOrderId(),request.getRazorpayOrderId())) throw new IllegalArgumentException("Order mismatch");
  try {
   if(!Utils.verifyPaymentSignature(new JSONObject().put("razorpay_order_id",s.getRazorpayOrderId()).put("razorpay_payment_id",request.getRazorpayPaymentId()).put("razorpay_signature",request.getRazorpaySignature()),props.getKeySecret())) throw new IllegalArgumentException("Invalid payment signature");
   settle(s,request.getRazorpayPaymentId());return view(s);
  } catch(com.razorpay.RazorpayException e) {throw new IllegalStateException("Payment verification unavailable. Retry verification from My Packages.",e);}
 }
 private void settle(UserPackageSubscription s,String paymentId) throws com.razorpay.RazorpayException {
  com.razorpay.Payment p=gateway.payments.fetch(paymentId);
  if(!Objects.equals(paymentId,p.get("id")) || !Objects.equals(s.getRazorpayOrderId(),p.get("order_id")) || !"captured".equals(p.get("status"))
   || !Boolean.TRUE.equals(p.get("captured")) || ((Number)p.get("amount")).intValue()!=s.getAmountCents() || !Objects.equals(s.getCurrency(),p.get("currency"))
   || ((Number)p.get("amount_refunded")).intValue()!=0) throw new IllegalStateException("Payment is not captured for this package and amount. Retry verification later.");
  if(s.getStatus()!=PackageStatus.PAYMENT_PENDING) return;
  s.setPaymentReference(paymentId);s.setPaymentStatus(PaymentStatus.CAPTURED);s.setStatus(PackageStatus.ACTIVE);
  s.setRemainingSessions(s.getPurchasedSessions());s.setActivatedAt(OffsetDateTime.now());
  s.setExpiryDate(s.getPurchasedValidityDays()==null || s.getPurchasedValidityDays()<=0?null:s.getActivatedAt().plusDays(s.getPurchasedValidityDays()));
  subscriptions.save(s);
 }
 @Transactional
 public void captured(String orderId,String paymentId) {
  subscriptions.findIdByOrder(orderId).ifPresent(id->{
   UserPackageSubscription s=subscriptions.findForUpdate(id).orElseThrow();
   try {enabled();settle(s,paymentId);} catch(com.razorpay.RazorpayException e) {throw new IllegalStateException("Package payment verification unavailable",e);}
  });
 }
 @Transactional
 public void refunded(String paymentId) {
  if(gateway==null)return;
  try {
   com.razorpay.Payment p=gateway.payments.fetch(paymentId);
   subscriptions.findIdByOrder(p.get("order_id")).ifPresent(id->{
    UserPackageSubscription s=subscriptions.findForUpdate(id).orElseThrow();
    if(!Objects.equals(s.getPaymentReference(),paymentId))return;
    if(((Number)p.get("amount_refunded")).intValue()>0) {
     s.setStatus(PackageStatus.CANCELLED);s.setRemainingSessions(0);
     s.setPaymentStatus(((Number)p.get("amount_refunded")).intValue()==s.getAmountCents()?PaymentStatus.REFUNDED:PaymentStatus.PARTIALLY_REFUNDED);
     subscriptions.save(s);
    }
   });
  } catch(com.razorpay.RazorpayException e) {throw new IllegalStateException("Unable to reconcile package refund",e);}
 }
}
