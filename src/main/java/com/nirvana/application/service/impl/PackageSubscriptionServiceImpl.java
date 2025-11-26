package com.nirvana.application.service.impl;

import com.nirvana.application.exception.NoActivePackageException;
import com.nirvana.application.exception.NoRemainingSessionsException;
import com.nirvana.application.exception.PackageNotEligibleForSpaException;
import com.nirvana.application.exception.PackagePaymentFailedException;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.ServicePackage;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.UserPackageSubscription;
import com.nirvana.application.model.UserPackageUsageLog;
import com.nirvana.application.model.dto.CreatePackagePurchaseRequest;
import com.nirvana.application.model.dto.PackageDetailsResponse;
import com.nirvana.application.model.dto.PackagePurchaseResponse;
import com.nirvana.application.model.dto.PackageUsageCheckResponse;
import com.nirvana.application.model.dto.PackageUsageConsumeRequest;
import com.nirvana.application.model.dto.PackageUsageConsumeResponse;
import com.nirvana.application.model.dto.PackageUsageHistoryResponse;
import com.nirvana.application.model.dto.PackageUsageLogItemResponse;
import com.nirvana.application.model.enums.BookingPaymentType;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PackageStatus;
import com.nirvana.application.model.enums.PackageType;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.ServicePackageRepository;
import com.nirvana.application.repository.UserPackageSubscriptionRepository;
import com.nirvana.application.repository.UserPackageUsageLogRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.PackageSubscriptionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PackageSubscriptionServiceImpl implements PackageSubscriptionService {

    private final ServicePackageRepository servicePackageRepository;
    private final UserPackageSubscriptionRepository subscriptionRepository;
    private final UserPackageUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public PackagePurchaseResponse purchasePackage(Long userId, CreatePackagePurchaseRequest request) {
        if (!isPaymentSuccessful(request.getPaymentStatus())) {
            throw new PackagePaymentFailedException("Payment not completed for package purchase");
        }

        ensureNoActivePackage(userId);

        ServicePackage servicePackage = servicePackageRepository.findByPackageTypeAndActiveTrue(request.getPackageType())
                .orElseThrow(() -> new EntityNotFoundException("Package not configured: " + request.getPackageType()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();
        int sessionCount = servicePackage.getSessionCount() != null
                ? servicePackage.getSessionCount()
                : request.getPackageType().getSessions();
        UserPackageSubscription subscription = new UserPackageSubscription();
        subscription.setUser(user);
        subscription.setServicePackage(servicePackage);
        subscription.setPackageType(servicePackage.getPackageType());
        subscription.setPurchaseDate(now);
        subscription.setActivatedAt(now);
        subscription.setPaymentStatus(request.getPaymentStatus());
        subscription.setPaymentReference(request.getPaymentReference());
        subscription.setRemainingSessions(sessionCount);
        subscription.setStatus(PackageStatus.ACTIVE);
        subscription.setExpiryDate(resolveExpiry(now, servicePackage.getValidityDays()));

        subscriptionRepository.save(subscription);
        log.info("Package {} purchased by user {}", subscription.getPackageType(), userId);

        return toPurchaseResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public PackageDetailsResponse getActivePackage(Long userId) {
        Optional<UserPackageSubscription> active = subscriptionRepository.findByUserIdAndStatus(userId, PackageStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new NoActivePackageException("No active package found for user");
        }
        UserPackageSubscription subscription = validateSubscription(active.get());
        return toDetailsResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public PackageUsageCheckResponse canUsePackage(Long userId, Long spaId) {
        PackageUsageCheckResponse response = new PackageUsageCheckResponse();
        try {
            UserPackageSubscription subscription = validateSubscription(fetchActiveSubscription(userId));
            boolean spaEligible = isSpaEligible(subscription.getServicePackage(), spaId);
            response.setSpaEligible(spaEligible);
            response.setPackageType(subscription.getPackageType());
            response.setRemainingSessions(subscription.getRemainingSessions());
            response.setCanUse(spaEligible && subscription.getRemainingSessions() > 0);
            if (!spaEligible) {
                response.setReason("SPA center not eligible for package usage");
            }
            if (Boolean.TRUE.equals(response.isCanUse()) && subscription.getRemainingSessions() <= 0) {
                response.setCanUse(false);
                response.setReason("No sessions remaining");
            }
        } catch (NoActivePackageException | NoRemainingSessionsException ex) {
            response.setCanUse(false);
            response.setReason(ex.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public PackageUsageConsumeResponse consumePackageSession(Long userId, PackageUsageConsumeRequest request) {
        UserPackageSubscription subscription = validateSubscription(
                fetchActiveSubscriptionForUpdate(userId));

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + request.getBookingId()));
        if (!booking.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Booking does not belong to user");
        }

        Spa spa = booking.getSpa();
        if (!spa.getId().equals(request.getSpaId())) {
            throw new IllegalArgumentException("Spa mismatch for booking");
        }

        if (!isSpaEligible(subscription.getServicePackage(), spa.getId())) {
            throw new PackageNotEligibleForSpaException("SPA center not eligible for package usage");
        }
        if (subscription.getRemainingSessions() <= 0) {
            subscription.setStatus(PackageStatus.EXHAUSTED);
            subscriptionRepository.save(subscription);
            throw new NoRemainingSessionsException("No package sessions left");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int nextSessionNumber = resolveSessionCount(subscription) - subscription.getRemainingSessions() + 1;
        subscription.setRemainingSessions(subscription.getRemainingSessions() - 1);
        subscription.setLastUsedAt(now);
        if (subscription.getRemainingSessions() <= 0) {
            subscription.setStatus(PackageStatus.EXHAUSTED);
        }
        subscriptionRepository.save(subscription);

        UserPackageUsageLog usageLog = UserPackageUsageLog.builder()
                .subscription(subscription)
                .user(subscription.getUser())
                .booking(booking)
                .spa(spa)
                .sessionNumber(nextSessionNumber)
                .usedAt(now)
                .build();
        usageLogRepository.save(usageLog);

        booking.setPaymentType(BookingPaymentType.PAID_BY_PACKAGE);
        booking.setPackageSubscription(subscription);
        booking.setPaymentMode(PaymentMode.OFFLINE);
        booking.setRemainderCents(0);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setLastStatusChangedAt(now);
        bookingRepository.save(booking);

        PackageUsageConsumeResponse response = new PackageUsageConsumeResponse();
        response.setBookingId(booking.getId());
        response.setSubscriptionId(subscription.getId());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setStatus(subscription.getStatus());
        response.setSessionNumber(nextSessionNumber);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PackageUsageHistoryResponse getUsageHistory(Long userId) {
        List<PackageUsageLogItemResponse> usages = usageLogRepository.findByUserIdOrderByUsedAtDesc(userId)
                .stream()
                .map(log -> {
                    PackageUsageLogItemResponse dto = new PackageUsageLogItemResponse();
                    dto.setBookingId(log.getBooking().getId());
                    dto.setSpaId(log.getSpa().getId());
                    dto.setSpaName(log.getSpa().getName());
                    dto.setSessionNumber(log.getSessionNumber());
                    dto.setUsedAt(log.getUsedAt());
                    return dto;
                })
                .toList();
        return new PackageUsageHistoryResponse(usages);
    }

    private void ensureNoActivePackage(Long userId) {
        Optional<UserPackageSubscription> active = subscriptionRepository.findByUserIdAndStatus(userId, PackageStatus.ACTIVE);
        if (active.isPresent()) {
            throw new IllegalStateException("User already has an active package");
        }
    }

    private UserPackageSubscription fetchActiveSubscription(Long userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, PackageStatus.ACTIVE)
                .orElseThrow(() -> new NoActivePackageException("No active package found for user"));
    }

    private UserPackageSubscription fetchActiveSubscriptionForUpdate(Long userId) {
        return subscriptionRepository.findByUserIdAndStatusForUpdate(userId, PackageStatus.ACTIVE)
                .orElseThrow(() -> new NoActivePackageException("No active package found for user"));
    }

    private UserPackageSubscription validateSubscription(UserPackageSubscription subscription) {
        if (subscription.getExpiryDate() != null && subscription.getExpiryDate().isBefore(OffsetDateTime.now())) {
            subscription.setStatus(PackageStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            throw new NoActivePackageException("Package expired");
        }
        if (subscription.getRemainingSessions() <= 0) {
            subscription.setStatus(PackageStatus.EXHAUSTED);
            subscriptionRepository.save(subscription);
            throw new NoRemainingSessionsException("No package sessions left");
        }
        return subscription;
    }

    private boolean isSpaEligible(ServicePackage servicePackage, Long spaId) {
        return servicePackage.getEligibleSpas().stream().anyMatch(spa -> spa.getId().equals(spaId));
    }

    private OffsetDateTime resolveExpiry(OffsetDateTime purchaseDate, Integer validityDays) {
        if (validityDays == null || validityDays <= 0) {
            return null;
        }
        return purchaseDate.plusDays(validityDays);
    }

    private boolean isPaymentSuccessful(PaymentStatus paymentStatus) {
        return paymentStatus == PaymentStatus.COMPLETED
                || paymentStatus == PaymentStatus.CAPTURED
                || paymentStatus == PaymentStatus.AUTHORIZED;
    }

    private PackagePurchaseResponse toPurchaseResponse(UserPackageSubscription subscription) {
        PackagePurchaseResponse response = new PackagePurchaseResponse();
        response.setSubscriptionId(subscription.getId());
        response.setPackageType(subscription.getPackageType());
        response.setStatus(subscription.getStatus());
        response.setPaymentStatus(subscription.getPaymentStatus());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setPurchaseDate(subscription.getPurchaseDate());
        response.setExpiryDate(subscription.getExpiryDate());
        response.setSessionCount(resolveSessionCount(subscription));
        response.setPriceCents(resolvePriceCents(subscription));
        return response;
    }

    private PackageDetailsResponse toDetailsResponse(UserPackageSubscription subscription) {
        PackageDetailsResponse response = new PackageDetailsResponse();
        response.setSubscriptionId(subscription.getId());
        response.setPackageType(subscription.getPackageType());
        response.setStatus(subscription.getStatus());
        response.setPaymentStatus(subscription.getPaymentStatus());
        response.setRemainingSessions(subscription.getRemainingSessions());
        response.setSessionCount(resolveSessionCount(subscription));
        response.setPurchaseDate(subscription.getPurchaseDate());
        response.setExpiryDate(subscription.getExpiryDate());
        return response;
    }

    private Integer resolveSessionCount(UserPackageSubscription subscription) {
        if (subscription.getServicePackage() != null && subscription.getServicePackage().getSessionCount() != null) {
            return subscription.getServicePackage().getSessionCount();
        }
        return subscription.getPackageType().getSessions();
    }

    private Integer resolvePriceCents(UserPackageSubscription subscription) {
        if (subscription.getServicePackage() != null && subscription.getServicePackage().getPriceCents() != null) {
            return subscription.getServicePackage().getPriceCents();
        }
        return subscription.getPackageType().getPriceCents();
    }
}
