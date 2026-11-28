package com.nirvana.application;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.CheckoutRecovery;
import com.nirvana.application.model.Payment;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.CheckoutReminderEmail;
import com.nirvana.application.model.dto.DaySlotsResponse;
import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.model.dto.WeekSlotsResponse;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.CancellationActor;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.PaymentStatus;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.model.enums.SlotStatus;
import com.nirvana.application.repository.CheckoutRecoveryRepository;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.EmailService;
import com.nirvana.application.service.addons.BookingAddOns;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import com.nirvana.application.service.recovery.CheckoutRecoveryService;
import com.nirvana.application.service.recovery.CheckoutRecoveryService.Skip;
import com.nirvana.application.service.recovery.CheckoutRecoveryTokens;
import com.nirvana.application.service.recovery.CheckoutRecoveryTokens.Purpose;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Abandoned-checkout reminders: who gets one, how often, the signed link, opting out and conversions. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckoutRecoveryTest {

    @Autowired BookingAddOns addOns;
    @Autowired CheckoutRecoveryService recovery;
    @Autowired CheckoutRecoveryTokens tokens;
    @Autowired CheckoutRecoveryRepository recoveries;
    @Autowired RoleRepository roles;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean EmailService email;
    @MockitoBean SlotAvailabilityService availability;

    OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        when(email.sendCheckoutReminder(any())).thenReturn(true);
        when(availability.getAvailableSlotsForServiceRange(anyLong(), anyLong(), any(), anyInt(), anyInt(), eq(0)))
                .thenReturn(new WeekSlotsResponse(1L, 1L, List.of()));
    }

    // ------------------ fixtures ------------------

    User guest(String email) {
        User u = User.builder().name("Asha").email(email).password("!test")
                .role(roles.findByRoleType(RoleType.CUSTOMER)).marketingOptIn(false).build();
        em.persist(u);
        return u;
    }

    User guest() {
        return guest("recovery-" + UUID.randomUUID() + "@example.test");
    }

    /** A checkout the system cancelled because payment never arrived, started {@code minutesAgo}. */
    Booking abandoned(User user, int minutesAgo, OffsetDateTime start) {
        Booking b = booking(user, 1L, start, BookingStatus.CANCELLED, PaymentMode.ONLINE);
        b.setCancelledBy(CancellationActor.SYSTEM);
        b.setCancellationReasonCode("PAYMENT_TIMEOUT");
        b.setCancelledAt(now.minusMinutes(minutesAgo - 30L));
        em.flush();
        return createdAt(b, now.minusMinutes(minutesAgo));
    }

    Booking abandoned(User user) {
        return abandoned(user, 45, now.plusDays(2));
    }

    Booking booking(User user, Long spaId, OffsetDateTime start, BookingStatus status, PaymentMode mode) {
        Spa spa = em.find(Spa.class, spaId);
        Service service = em.createQuery("select s from Service s where s.spa.id = :spa order by s.id", Service.class)
                .setParameter("spa", spaId).setMaxResults(1).getSingleResult();
        Slot slot = new Slot();
        slot.setSpa(spa);
        slot.setService(service);
        slot.setStartTs(start);
        slot.setEndTs(start.plusHours(1));
        slot.setStatus(SlotStatus.OPEN);
        slot.setBookedUnits((short) 0);
        em.persist(slot);

        Booking b = new Booking();
        b.setBookingReference("REC-" + UUID.randomUUID());
        b.setUser(user);
        b.setSpa(spa);
        b.setService(service);
        b.setSlot(slot);
        b.setStartTs(start);
        b.setEndTs(start.plusHours(1));
        b.setStatus(status);
        b.setPaymentMode(mode);
        b.setScheduledBy(CancellationActor.USER);
        b.setPriceCents(200_000);
        b.setTaxCents(36_000);
        b.setDiscountCents(10_000);
        b.setDepositCents(0);
        b.setRemainderCents(226_000);
        b.setPolicySnapshotJson("{}");
        em.persist(b);
        em.flush();
        return b;
    }

    Booking createdAt(Booking b, OffsetDateTime at) {
        em.createQuery("update Booking b set b.createdAt = :at where b.id = :id")
                .setParameter("at", at).setParameter("id", b.getId()).executeUpdate();
        em.clear();
        return em.find(Booking.class, b.getId());
    }

    void payment(Booking b, PaymentStatus status, String linkId) {
        Payment p = new Payment();
        p.setBooking(em.getReference(Booking.class, b.getId()));
        p.setGateway("RAZORPAY");
        p.setPaymentStatus(status);
        p.setTotalPrice(new BigDecimal("2260.00"));
        p.setAmountCents(226_000);
        p.setPaymentLinkId(linkId);
        em.persist(p);
        em.flush();
    }

    Skip skipOf(Booking b) {
        return recovery.claim(b.getId(), now).skipped();
    }

    CheckoutRecoveryService.Claim sent(Booking b) {
        var outcome = recovery.claim(b.getId(), now);
        assertNull(outcome.skipped(), "expected a reminder, got " + outcome.skipped());
        assertTrue(recovery.deliver(outcome.claim()));
        return outcome.claim();
    }

    UsernamePasswordAuthenticationToken as(User u, String role) {
        var principal = new UserPrincipal(u.getId(), "u" + u.getId(), u.getEmail(), "", true, List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    String token(CheckoutRecoveryService.Claim claim, Purpose purpose) {
        CheckoutRecovery row = recoveries.findById(claim.recoveryId()).orElseThrow();
        return tokens.sign(row.getId(), row.getTokenNonce(), purpose, row.getTokenExpiresAt().toInstant());
    }

    // ------------------ sending ------------------

    @Test
    void remindsOnceByEmailWithSignedLinksAndNoPersonalData() {
        User u = guest();
        Booking b = abandoned(u);
        assertTrue(recovery.candidates(now, 500).contains(b.getId()));

        var claim = sent(b);
        assertEquals(CheckoutRecovery.Channel.EMAIL, claim.channel());
        var mail = ArgumentCaptor.forClass(CheckoutReminderEmail.class);
        verify(email).sendCheckoutReminder(mail.capture());
        CheckoutReminderEmail m = mail.getValue();
        assertEquals(u.getEmail(), m.toEmail());
        assertTrue(m.resumeUrl().contains("/booking/resume/"));
        assertTrue(m.unsubscribeUrl().contains("/booking/stop-reminders?t="));
        assertTrue(m.oneClickUnsubscribeUrl().contains("/api/public/checkout-reminders/unsubscribe?t="));
        for (String url : List.of(m.resumeUrl(), m.unsubscribeUrl(), m.oneClickUnsubscribeUrl())) {
            assertFalse(url.contains(u.getEmail()) || url.contains("recovery-") || url.contains(b.getBookingReference())
                    || url.contains("Asha"), "no personal data in " + url);
        }
        assertEquals(CheckoutRecovery.Status.SENT, recoveries.findById(claim.recoveryId()).orElseThrow().getStatus());

        // At most one reminder per abandoned checkout.
        assertFalse(recovery.candidates(now, 500).contains(b.getId()));
        assertEquals(Skip.ALREADY_REMINDED, skipOf(b));
    }

    @Test
    void aFailedSendIsRecordedAndNotRetried() {
        when(email.sendCheckoutReminder(any())).thenReturn(false);
        Booking b = abandoned(guest());
        var claim = recovery.claim(b.getId(), now).claim();
        assertFalse(recovery.deliver(claim));
        assertEquals(CheckoutRecovery.Status.FAILED, recoveries.findById(claim.recoveryId()).orElseThrow().getStatus());
        assertFalse(recovery.deliver(claim));
        assertEquals(Skip.ALREADY_REMINDED, skipOf(b));
        verify(email, times(1)).sendCheckoutReminder(any());
    }

    @Test
    void anUnpaidCheckoutStillPendingAfterItsPaymentWindowIsReminded() {
        User u = guest();
        Booking b = createdAt(booking(u, 1L, now.plusDays(2), BookingStatus.PENDING_PAYMENT, PaymentMode.ONLINE), now.minusMinutes(45));
        payment(b, PaymentStatus.INIT, null);
        assertNull(skipOf(b));
    }

    // ------------------ each skip rule ------------------

    @Test
    void skipsPayAtSpaAndBookingsThatWereNotAbandoned() {
        User u = guest();
        Booking offline = createdAt(booking(u, 1L, now.plusDays(2), BookingStatus.CONFIRMED, PaymentMode.OFFLINE), now.minusMinutes(45));
        assertEquals(Skip.NOT_ONLINE, skipOf(offline));

        Booking confirmed = createdAt(booking(u, 1L, now.plusDays(2), BookingStatus.CONFIRMED, PaymentMode.ONLINE), now.minusMinutes(45));
        assertEquals(Skip.NOT_ABANDONED, skipOf(confirmed));

        Booking cancelledByGuest = booking(u, 1L, now.plusDays(2), BookingStatus.CANCELLED, PaymentMode.ONLINE);
        cancelledByGuest.setCancelledBy(CancellationActor.USER);
        cancelledByGuest.setCancellationReasonCode("CHANGE_OF_PLANS");
        em.flush();
        assertEquals(Skip.NOT_ABANDONED, skipOf(createdAt(cancelledByGuest, now.minusMinutes(45))));

        Booking deskBooking = abandoned(u);
        deskBooking.setScheduledBy(CancellationActor.SPA);
        em.flush();
        assertEquals(Skip.BOOKED_BY_STAFF, skipOf(deskBooking));
    }

    @Test
    void skipsCheckoutsThatWerePaidOrSentAPaymentLink() {
        Booking paid = abandoned(guest());
        payment(paid, PaymentStatus.CAPTURED, null);
        assertEquals(Skip.PAID, skipOf(paid));

        Booking link = abandoned(guest());
        payment(link, PaymentStatus.CANCELLED, "plink_test");
        assertEquals(Skip.PAYMENT_LINK, skipOf(link));
    }

    @Test
    void waitsForTheRightMomentAndSkipsSlotsThatAreTooClose() {
        User u = guest();
        Booking stillPaying = createdAt(booking(u, 1L, now.plusDays(2), BookingStatus.PENDING_PAYMENT, PaymentMode.ONLINE), now.minusMinutes(10));
        assertEquals(Skip.STILL_PAYABLE, skipOf(stillPaying));
        assertEquals(Skip.TOO_RECENT, skipOf(abandoned(u, 20, now.plusDays(2))));
        assertEquals(Skip.TOO_OLD, skipOf(abandoned(u, 7 * 60, now.plusDays(2))));
        assertEquals(Skip.SLOT_TOO_SOON, skipOf(abandoned(u, 45, now.plusMinutes(90))));
        assertEquals(Skip.SLOT_TOO_SOON, skipOf(abandoned(u, 45, now.minusMinutes(5))));
    }

    @Test
    void skipsGuestsWhoBookedSinceOptedOutOrCannotBeReached() {
        User booked = guest();
        Booking left = abandoned(booked);
        createdAt(booking(booked, 1L, now.plusDays(3), BookingStatus.CONFIRMED, PaymentMode.ONLINE), now.minusMinutes(20));
        assertEquals(Skip.BOOKED_SINCE, skipOf(left));

        User optedOut = guest();
        recovery.setOptedOut(optedOut.getId(), true);
        assertEquals(Skip.OPTED_OUT, skipOf(abandoned(optedOut)));

        User inactive = guest();
        inactive.setDeletedAt(now.minusDays(1));
        em.flush();
        assertEquals(Skip.ACCOUNT_INACTIVE, skipOf(abandoned(inactive)));

        User noEmail = guest(null);
        assertEquals(Skip.NO_CONTACT, skipOf(abandoned(noEmail)));
    }

    // ------------------ rate limit ------------------

    @Test
    void sendsAtMostOneReminderPerGuestPerDay() {
        User u = guest();
        Booking first = abandoned(u, 50, now.plusDays(2));
        Booking second = abandoned(u, 45, now.plusDays(4));
        sent(second);
        assertEquals(Skip.RATE_LIMITED, skipOf(first));

        // A reminder from more than 24 hours ago doesn't count.
        User later = guest();
        Booking old = abandoned(later, 45, now.plusDays(2));
        var claim = sent(old);
        CheckoutRecovery row = recoveries.findById(claim.recoveryId()).orElseThrow();
        row.setSentAt(now.minusHours(25));
        recoveries.saveAndFlush(row);
        assertNull(skipOf(abandoned(later, 45, now.plusDays(5))));
    }

    // ------------------ tokens ------------------

    @Test
    void linksAreSignedPurposeBoundAndExpire() {
        String nonce = CheckoutRecoveryTokens.newNonce();
        Instant expires = now.plusHours(48).toInstant();
        String token = tokens.sign(42L, nonce, Purpose.RESUME, expires);

        var claims = tokens.verify(token, Purpose.RESUME, now.toInstant()).orElseThrow();
        assertEquals(42L, claims.recoveryId());
        assertEquals(nonce, claims.nonce());
        assertTrue(token.matches("[A-Za-z0-9_.-]+"), "URL-safe");

        assertTrue(tokens.verify(token, Purpose.UNSUBSCRIBE, now.toInstant()).isEmpty(), "bound to its purpose");
        assertTrue(tokens.verify(token, Purpose.RESUME, expires).isEmpty(), "expired at its expiry");
        assertTrue(tokens.verify(token, Purpose.RESUME, expires.plusSeconds(3600)).isEmpty(), "expired after");

        String forgedBody = tokens.sign(43L, nonce, Purpose.RESUME, expires).split("\\.")[0] + "." + token.split("\\.")[1];
        assertTrue(tokens.verify(forgedBody, Purpose.RESUME, now.toInstant()).isEmpty(), "body swapped");
        char last = token.charAt(token.length() - 1);
        String flipped = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertTrue(tokens.verify(flipped, Purpose.RESUME, now.toInstant()).isEmpty(), "signature changed");
        assertTrue(tokens.verify("garbage", Purpose.RESUME, now.toInstant()).isEmpty());
        assertTrue(tokens.verify(null, Purpose.RESUME, now.toInstant()).isEmpty());

        // A genuine signature with a nonce that isn't on the row is refused too.
        Booking b = abandoned(guest());
        var claim = sent(b);
        String wrongNonce = tokens.sign(claim.recoveryId(), CheckoutRecoveryTokens.newNonce(), Purpose.RESUME, expires);
        assertThrows(EntityNotFoundException.class, () -> recovery.resume(wrongNonce, b.getUser().getId(), now));
        String expired = tokens.sign(claim.recoveryId(), recoveries.findById(claim.recoveryId()).orElseThrow().getTokenNonce(),
                Purpose.RESUME, now.minusMinutes(1).toInstant());
        assertThrows(EntityNotFoundException.class, () -> recovery.resume(expired, b.getUser().getId(), now));
    }

    // ------------------ resuming and clicks ------------------

    @Test
    void resumeUsesCurrentAddOnDurationAndFlagsChangedTerms() {
        Booking b = abandoned(guest());
        String currency = b.getService().getCurrency();
        var extra = addOns.create(1L, new BookingAddOns.Input("Extra time", null, 50000, currency, 30, true, 0, List.of(), null));
        var selection = addOns.select(b.getSpa(), b.getService(), List.of(extra.id()), 1);
        addOns.record(b.getId(), selection);
        var claim = sent(b);
        addOns.update(1L, extra.id(), new BookingAddOns.Input("Extra time", null, 50000, currency, 45, true, 0, List.of(), extra.version()));
        when(availability.getAvailableSlotsForServiceRange(eq(1L), eq(b.getService().getId()), any(), anyInt(), eq(1), eq(45)))
                .thenReturn(new WeekSlotsResponse(1L, b.getService().getId(), List.of()));
        var result = recovery.resume(token(claim, Purpose.RESUME), b.getUser().getId(), now);
        assertTrue(result.addOnsChanged());
        assertEquals(List.of(extra.id()), result.addOns().stream().map(BookingAddOns.AddOn::id).toList());
        verify(availability).getAvailableSlotsForServiceRange(eq(1L), eq(b.getService().getId()), any(), anyInt(), eq(1), eq(45));
    }

    @Test
    void paidCheckoutCannotBeRestartedFromAnOldReminder() {
        Booking b = abandoned(guest());
        var claim = sent(b);
        payment(b, PaymentStatus.CAPTURED, null);
        var result = recovery.resume(token(claim, Purpose.RESUME), b.getUser().getId(), now);
        assertTrue(result.checkoutResolved());
        assertTrue(result.alternatives().isEmpty());
        verifyNoInteractions(availability);
    }

    @Test
    void groupReminderDoesNotOfferASingleGuestSlot() {
        Booking b = abandoned(guest());
        var claim = sent(b);
        jdbc.update("INSERT INTO booking_group(spa_id,booker_user_id,group_size,start_ts,currency,total_cents,payment_mode,created_at,updated_at) VALUES(?,?,2,?,'INR',400000,'ONLINE',NOW(),NOW())",
                b.getSpa().getId(), b.getUser().getId(), java.sql.Timestamp.from(b.getStartTs().toInstant()));
        Long groupId = jdbc.queryForObject("SELECT id FROM booking_group WHERE booker_user_id=?", Long.class, b.getUser().getId());
        jdbc.update("INSERT INTO booking_group_member(booking_id,group_id,position,guest_name,is_booker) VALUES(?,?,1,'Guest',TRUE)", b.getId(), groupId);
        var result = recovery.resume(token(claim, Purpose.RESUME), b.getUser().getId(), now);
        assertEquals(groupId, result.groupId());
        assertFalse(result.originalAvailable());
        assertTrue(result.alternatives().isEmpty());
        verifyNoInteractions(availability);
    }

    @Test
    void resumePrefillsTheSameChoicesAndRecordsTheClick() throws Exception {
        User u = guest();
        OffsetDateTime start = now.plusDays(2).withMinute(0).withSecond(0);
        Booking b = abandoned(u, 45, start);
        b.setGuestCount(2);
        b.setTherapistPreference("LABEL");
        b.setTherapistType("Female therapist");
        em.flush();
        var claim = sent(b);
        String token = token(claim, Purpose.RESUME);
        LocalDate day = start.toLocalDate();
        when(availability.getAvailableSlotsForServiceRange(eq(1L), eq(b.getService().getId()), any(), anyInt(), eq(2), eq(0)))
                .thenReturn(new WeekSlotsResponse(1L, b.getService().getId(), List.of(new DaySlotsResponse(day, List.of(
                        slot(901L, start, 2), slot(902L, start.plusHours(1), 2))))));

        mvc.perform(get("/api/me/checkout-recovery/" + token).with(authentication(as(u, "ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaId").value(1))
                .andExpect(jsonPath("$.serviceId").value(b.getService().getId()))
                .andExpect(jsonPath("$.guests").value(2))
                .andExpect(jsonPath("$.therapistType").value("Female therapist"))
                .andExpect(jsonPath("$.originalAvailable").value(true))
                .andExpect(jsonPath("$.originalSlotId").value(901))
                .andExpect(jsonPath("$.alternatives").isEmpty());
        mvc.perform(get("/api/me/checkout-recovery/" + token).with(authentication(as(u, "ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
        CheckoutRecovery row = recoveries.findById(claim.recoveryId()).orElseThrow();
        assertNotNull(row.getClickedAt());
        assertEquals(2, row.getClickCount());

        // Someone else signed in with the link sees nothing and records no click.
        mvc.perform(get("/api/me/checkout-recovery/" + token).with(authentication(as(guest(), "ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
        assertEquals(2, recoveries.findById(claim.recoveryId()).orElseThrow().getClickCount());
    }

    @Test
    void whenTheTimeWasTakenResumeOffersTheNearestFreeTimes() {
        User u = guest();
        OffsetDateTime start = now.plusDays(2).withHour(12).withMinute(0).withSecond(0);
        Booking b = abandoned(u, 45, start);
        var claim = sent(b);
        LocalDate day = start.toLocalDate();
        // The original 12:00 is gone; 9:00 is too far, 11:00 and 14:00 are near, 13:00 is full.
        when(availability.getAvailableSlotsForServiceRange(eq(1L), anyLong(), any(), anyInt(), anyInt(), eq(0)))
                .thenReturn(new WeekSlotsResponse(1L, 1L, List.of(new DaySlotsResponse(day, List.of(
                        slot(1L, start.minusHours(3), 1), slot(2L, start.minusHours(1), 1), slot(3L, start.plusHours(1), 0),
                        slot(4L, start.plusHours(2), 1), slot(5L, start.plusHours(5), 1), slot(6L, start.plusHours(6), 1),
                        slot(7L, start.plusHours(7), 1), slot(8L, start.plusHours(8), 1), slot(9L, start.plusHours(9), 1))))));

        var resume = recovery.resume(token(claim, Purpose.RESUME), u.getId(), now);
        assertFalse(resume.originalAvailable());
        assertNull(resume.originalSlotId());
        List<Long> offered = resume.alternatives().stream().map(CheckoutRecoveryService.AlternativeTime::slotId).toList();
        assertEquals(List.of(1L, 2L, 4L, 5L, 6L, 7L), offered, "six nearest bookable times, in time order");
    }

    static SlotAvailabilityResponse slot(Long id, OffsetDateTime start, int remaining) {
        return new SlotAvailabilityResponse(id, start, start.plusHours(1), remaining, null, remaining > 0 ? "AVAILABLE" : "FULL",
                false, false, List.of(), List.of());
    }

    // ------------------ opting out ------------------

    @Test
    void theUnsubscribeLinkWorksWithoutSigningInAndCanBeUndone() throws Exception {
        User u = guest();
        var claim = sent(abandoned(u));
        String unsubscribe = token(claim, Purpose.UNSUBSCRIBE);

        mvc.perform(post("/api/public/checkout-reminders/unsubscribe").param("t", unsubscribe)
                        .contentType("application/x-www-form-urlencoded").content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersEnabled").value(false));
        assertTrue(recovery.optedOut(u.getId()));
        Booking next = abandoned(u, 45, now.plusDays(6));
        assertEquals(Skip.OPTED_OUT, skipOf(next));

        // A resume token can't be used to unsubscribe, and nonsense is refused.
        mvc.perform(post("/api/public/checkout-reminders/unsubscribe").param("t", token(claim, Purpose.RESUME)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/public/checkout-reminders/unsubscribe").param("t", "nope"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/public/checkout-reminders/resubscribe").param("t", unsubscribe))
                .andExpect(status().isOk());
        assertFalse(recovery.optedOut(u.getId()));
        // Back on: the only thing holding the next one back now is the one-a-day limit.
        assertEquals(Skip.RATE_LIMITED, skipOf(next));

        mvc.perform(get("/api/me/checkout-reminders").with(authentication(as(u, "ROLE_CUSTOMER"))))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    // ------------------ conversions and stats ------------------

    @Test
    void aLaterBookingAtTheSameSpaCountsAsRecoveredRevenue() throws Exception {
        User u = guest();
        var claim = sent(abandoned(u));
        recovery.resume(token(claim, Purpose.RESUME), u.getId(), now);
        assertEquals(0, recovery.recordConversions(now));

        Long otherSpa = jdbc.query("SELECT id FROM spa WHERE id <> 1 AND EXISTS (SELECT 1 FROM service s WHERE s.spa_id = spa.id) ORDER BY id LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (otherSpa != null) {
            createdAt(booking(u, otherSpa, now.plusDays(3), BookingStatus.CONFIRMED, PaymentMode.ONLINE), now.plusMinutes(5));
            assertEquals(0, recovery.recordConversions(now.plusMinutes(10)), "another spa is not a recovery");
        }
        Booking rebooked = createdAt(booking(u, 1L, now.plusDays(3), BookingStatus.CONFIRMED, PaymentMode.ONLINE), now.plusMinutes(6));
        assertEquals(1, recovery.recordConversions(now.plusMinutes(10)));
        CheckoutRecovery row = recoveries.findById(claim.recoveryId()).orElseThrow();
        assertEquals(rebooked.getId(), row.getConvertedBookingId());
        assertEquals(226_000, row.getRecoveredCents());
        assertEquals(0, recovery.recordConversions(now.plusMinutes(11)), "counted once");

        var stats = recovery.stats(now.minusDays(1), now.plusDays(1), 1L);
        assertTrue(stats.sent() >= 1 && stats.clicked() >= 1 && stats.converted() >= 1);
        assertTrue(stats.recoveredCents() >= 226_000);

        User admin = guest();
        mvc.perform(get("/api/admin/checkout-recovery/stats").with(authentication(as(admin, "ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").isNumber());
        mvc.perform(get("/api/admin/checkout-recovery/stats").with(authentication(as(u, "ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/owner/spas/1/checkout-recovery/stats").with(authentication(as(u, "ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversionsOutsideTheAttributionWindowDoNotCount() {
        User u = guest();
        var claim = sent(abandoned(u));
        createdAt(booking(u, 1L, now.plusDays(9), BookingStatus.CONFIRMED, PaymentMode.ONLINE), now.plusDays(8));
        assertEquals(0, recovery.recordConversions(now.plusDays(8).plusMinutes(1)));
        assertNull(recoveries.findById(claim.recoveryId()).orElseThrow().getConvertedAt());
    }
}
