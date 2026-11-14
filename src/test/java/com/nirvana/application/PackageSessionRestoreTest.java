package com.nirvana.application;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.UserPackageSubscription;
import com.nirvana.application.model.UserPackageUsageLog;
import com.nirvana.application.model.enums.PackageStatus;
import com.nirvana.application.model.enums.spa.UserSpaPackageStatus;
import com.nirvana.application.model.spa.UserSpaPackageSubscription;
import com.nirvana.application.model.spa.UserSpaPackageUsageLog;
import com.nirvana.application.repository.UserPackageSubscriptionRepository;
import com.nirvana.application.repository.UserPackageUsageLogRepository;
import com.nirvana.application.repository.UserSpaPackageSubscriptionRepository;
import com.nirvana.application.repository.UserSpaPackageUsageLogRepository;
import com.nirvana.application.service.impl.PackageSessionRestorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A cancelled booking gives its package session back, exactly once, to a subscription that can use it. */
class PackageSessionRestoreTest {
    UserPackageUsageLogRepository centralUsage = mock(UserPackageUsageLogRepository.class);
    UserPackageSubscriptionRepository centralSubs = mock(UserPackageSubscriptionRepository.class);
    UserSpaPackageUsageLogRepository spaUsage = mock(UserSpaPackageUsageLogRepository.class);
    UserSpaPackageSubscriptionRepository spaSubs = mock(UserSpaPackageSubscriptionRepository.class);
    PackageSessionRestorer restorer = new PackageSessionRestorer(centralUsage, centralSubs, spaUsage, spaSubs);
    Booking booking = new Booking();
    User user = mock(User.class);
    Spa spa = mock(Spa.class);

    @BeforeEach void setUp() {
        booking.setId(50L);
        when(user.getId()).thenReturn(3L);
        when(spa.getId()).thenReturn(1L);
    }

    UserPackageSubscription central(long id, PackageStatus status, int remaining, OffsetDateTime expiry) {
        var s = UserPackageSubscription.builder().id(id).user(user).status(status).remainingSessions(remaining).expiryDate(expiry).build();
        when(centralSubs.findForUpdate(id)).thenReturn(Optional.of(s));
        return s;
    }
    UserPackageUsageLog centralUse(UserPackageSubscription s) {
        var usage = UserPackageUsageLog.builder().id(900L).subscription(s).booking(booking).build();
        when(centralUsage.findByBooking_Id(50L)).thenReturn(List.of(usage));
        return usage;
    }
    UserSpaPackageSubscription spaSub(long id, UserSpaPackageStatus status, int remaining, OffsetDateTime expiry) {
        var s = UserSpaPackageSubscription.builder().id(id).user(user).spa(spa).status(status).remainingSessions(remaining).expiryDate(expiry).build();
        when(spaSubs.findForUpdate(id)).thenReturn(Optional.of(s));
        return s;
    }
    UserSpaPackageUsageLog spaUse(UserSpaPackageSubscription s) {
        var usage = UserSpaPackageUsageLog.builder().id(901L).subscription(s).booking(booking).build();
        when(spaUsage.findByBooking_Id(50L)).thenReturn(List.of(usage));
        return usage;
    }

    @Test void bookingWithoutAPackageRestoresNothing() {
        assertEquals(0, restorer.restoreFor(booking));
        verifyNoInteractions(centralSubs, spaSubs);
    }

    @Test void activeCentralPackageGetsItsSessionBackAndTheUsageRecordIsRemoved() {
        var sub = central(10, PackageStatus.ACTIVE, 2, null);
        var usage = centralUse(sub);
        assertEquals(1, restorer.restoreFor(booking));
        assertEquals(3, sub.getRemainingSessions());
        assertEquals(PackageStatus.ACTIVE, sub.getStatus());
        verify(centralUsage).delete(usage); // with the record gone, a second call finds nothing to restore
    }

    @Test void exhaustedCentralPackageBecomesUsableAgain() {
        var sub = central(10, PackageStatus.EXHAUSTED, 0, OffsetDateTime.now().plusDays(30));
        centralUse(sub);
        when(centralSubs.findByUserIdOrderByPurchaseDateDesc(3L)).thenReturn(List.of(sub));
        restorer.restoreFor(booking);
        assertEquals(1, sub.getRemainingSessions());
        assertEquals(PackageStatus.ACTIVE, sub.getStatus());
    }

    @Test void sessionGoesToTheNewerPackageWhenTheCustomerAlreadyBoughtAnother() {
        var old = central(10, PackageStatus.EXHAUSTED, 0, null);
        var current = central(11, PackageStatus.ACTIVE, 4, null);
        centralUse(old);
        when(centralSubs.findByUserIdOrderByPurchaseDateDesc(3L)).thenReturn(List.of(current, old));
        restorer.restoreFor(booking);
        assertEquals(5, current.getRemainingSessions());
        assertEquals(0, old.getRemainingSessions());
        assertEquals(PackageStatus.EXHAUSTED, old.getStatus(), "two active packages would break package checks");
    }

    @Test void expiredPackageIsCreditedButStaysExpired() {
        var sub = central(10, PackageStatus.EXHAUSTED, 0, OffsetDateTime.now().minusDays(1));
        centralUse(sub);
        restorer.restoreFor(booking);
        assertEquals(1, sub.getRemainingSessions());
        assertEquals(PackageStatus.EXHAUSTED, sub.getStatus());
    }

    @Test void spaPackageGetsItsSessionBack() {
        var sub = spaSub(20, UserSpaPackageStatus.EXHAUSTED, 0, null);
        var usage = spaUse(sub);
        when(spaSubs.findFirstByUserIdAndSpaIdAndStatus(3L, 1L, UserSpaPackageStatus.ACTIVE)).thenReturn(Optional.empty());
        assertEquals(1, restorer.restoreFor(booking));
        assertEquals(1, sub.getRemainingSessions());
        assertEquals(UserSpaPackageStatus.ACTIVE, sub.getStatus());
        verify(spaUsage).delete(usage);
    }

    @Test void spaPackageSessionGoesToTheNewerPackageForThatSpa() {
        var old = spaSub(20, UserSpaPackageStatus.EXHAUSTED, 0, null);
        var current = spaSub(21, UserSpaPackageStatus.ACTIVE, 2, null);
        spaUse(old);
        when(spaSubs.findFirstByUserIdAndSpaIdAndStatus(3L, 1L, UserSpaPackageStatus.ACTIVE)).thenReturn(Optional.of(current));
        restorer.restoreFor(booking);
        assertEquals(3, current.getRemainingSessions());
        assertEquals(UserSpaPackageStatus.EXHAUSTED, old.getStatus());
    }
}
