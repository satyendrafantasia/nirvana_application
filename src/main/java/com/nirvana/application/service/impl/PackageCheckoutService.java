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
 /** A Nirvana package for sale and how many published spas take it; the spas themselves come from {@link #findSpas}. */
 public record CatalogItem(Long id,String packageType,String description,int amountCents,String currency,
   int sessions,Integer validityDays,long eligibleSpaCount) {}
 /** A published spa that takes Nirvana packages, and which ones. */
 public record SpaMatch(Long id,String name,String city,List<Long> packageIds) {}
 public record SpaMatches(long total,List<SpaMatch> spas) {}
 /** Guests only ever see published spas. */
 private static final String PUBLISHED=ServicePackageRepository.REDEEMABLE_SPA;
 public record Purchase(Long subscriptionId,String packageType,String status,String paymentStatus,int remainingSessions,
   Integer sessions,Integer amountCents,String currency,OffsetDateTime expiryDate,Long packageId) {}
 public record Checkout(Purchase purchase,String razorpayKeyId,String razorpayOrderId,Integer amountCents,String currency) {}
 /**
  * Active packages with a count of published spas each, rather than every spa (3,000+ of them made
  * this response 800 KB). With {@code spaId}, only the packages that can be used at that spa.
  */
 @Transactional(readOnly=true)
 public List<CatalogItem> catalog(@Nullable Long spaId) {
  Map<Long,Long> counts=new HashMap<>();
  for(Object row:em.createNativeQuery("SELECT ps.package_id, COUNT(*) FROM service_package_spa ps JOIN spa s ON s.id=ps.spa_id WHERE "
    +PUBLISHED+" GROUP BY ps.package_id").getResultList()){Object[] r=(Object[])row;counts.put(((Number)r[0]).longValue(),((Number)r[1]).longValue());}
  Set<Long> atSpa=spaId==null?null:new HashSet<>(((List<?>)em.createNativeQuery("SELECT ps.package_id FROM service_package_spa ps JOIN spa s ON s.id=ps.spa_id WHERE ps.spa_id=?1 AND "+PUBLISHED)
    .setParameter(1,spaId).getResultList()).stream().map(v->((Number)v).longValue()).toList());
  return packages.findAll().stream().filter(p->Boolean.TRUE.equals(p.getActive())).filter(p->atSpa==null||atSpa.contains(p.getId()))
   .sorted(Comparator.comparing(ServicePackage::getPriceCents,Comparator.nullsLast(Comparator.naturalOrder())))
   .map(p->new CatalogItem(p.getId(),p.getPackageType().name(),p.getDescription(),p.getPriceCents(),props.getCurrency(),p.getSessionCount(),
   p.getValidityDays(),counts.getOrDefault(p.getId(),0L))).toList();
 }

 /**
  * Published spas that take Nirvana packages whose name or city contains every word searched,
  * names starting with the search first; optionally only spas for one package. At most {@code limit}.
  */
 @Transactional(readOnly=true)
 public SpaMatches findSpas(@Nullable String query,@Nullable Long packageId,int limit) {
  List<String> words=Arrays.stream((query==null?"":query.length()>100?query.substring(0,100):query).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
    .filter(w->!w.isBlank()).limit(5).toList();
  StringBuilder where=new StringBuilder(" WHERE "+PUBLISHED);
  // Existing subscribers can still find redemption locations for a retired package.
  if(packageId==null) where.append(" AND p.is_active=1");
  List<Object> args=new ArrayList<>();
  if(packageId!=null){where.append(" AND ps.package_id=?");args.add(packageId);}
  for(String w:words){where.append(" AND LOWER(CONCAT(s.name,' ',COALESCE(s.city,''))) LIKE ?");args.add("%"+w.replace("\\","").replace("%","").replace("_","")+"%");}
  String from=" FROM service_package_spa ps JOIN spa s ON s.id=ps.spa_id JOIN packages p ON p.id=ps.package_id";
  var count=em.createNativeQuery("SELECT COUNT(DISTINCT s.id)"+from+where);
  for(int i=0;i<args.size();i++)count.setParameter(i+1,args.get(i));
  long total=((Number)count.getSingleResult()).longValue();
  String first=words.isEmpty()?"":"CASE WHEN LOWER(s.name) LIKE ? THEN 0 ELSE 1 END, ";
  var q=em.createNativeQuery("SELECT s.id, s.name, s.city, GROUP_CONCAT(ps.package_id ORDER BY ps.package_id)"+from+where
    +" GROUP BY s.id, s.name, s.city ORDER BY "+first+"s.name LIMIT "+Math.max(1,Math.min(limit,100)));
  int i=1;for(Object a:args)q.setParameter(i++,a);
  if(!words.isEmpty())q.setParameter(i,String.join(" ",words)+"%");
  List<SpaMatch> spas=new ArrayList<>();
  for(Object row:q.getResultList()){Object[] r=(Object[])row;
   spas.add(new SpaMatch(((Number)r[0]).longValue(),(String)r[1],(String)r[2],
     Arrays.stream(String.valueOf(r[3]).split(",")).filter(v->!v.isBlank()).map(Long::valueOf).toList()));}
  return new SpaMatches(total,spas);
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
  props.requireCheckout(); enabled();
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
  requireSaleLocations(p);
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
 public Checkout resume(Long userId,Long id) {props.requireCheckout(); enabled();return resumeInternal(owned(userId,id));}
 private Checkout resumeInternal(UserPackageSubscription s) {
  if(s.getStatus()==PackageStatus.PAYMENT_PENDING) try {
   for(com.razorpay.Payment p:gateway.orders.fetchPayments(s.getRazorpayOrderId())) if("captured".equals(p.get("status"))) {settle(s,p.get("id"));break;}
  } catch(Exception e) {throw new IllegalStateException("Unable to verify payment. Retry this purchase before paying again.",e);}
  if(s.getStatus()==PackageStatus.PAYMENT_PENDING) requireSaleLocations(s.getServicePackage());
  return intent(s);
 }
 private void requireSaleLocations(ServicePackage p) {
  if(!Boolean.TRUE.equals(p.getActive()) || packages.countRedeemableSpas(p.getId())==0)
   throw new IllegalStateException("This package is currently unavailable for purchase. No payment has been requested.");
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
