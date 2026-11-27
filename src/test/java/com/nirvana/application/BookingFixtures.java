package com.nirvana.application;

import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.Slot;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.PaymentMode;
import com.nirvana.application.model.enums.SlotStatus;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.UUID;

final class BookingFixtures {
    private BookingFixtures() {}

    /** A new slot and booking for the seeded spa, service and customer, in the caller's transaction. */
    static Booking payAtSpa(EntityManager em, OffsetDateTime start, BookingStatus status) {
        Spa spa = em.find(Spa.class, 1L);
        Service service = em.getReference(Service.class, 1L);
        Slot slot = new Slot();
        slot.setSpa(spa);
        slot.setService(service);
        slot.setStartTs(start);
        slot.setEndTs(start.plusHours(1));
        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookedUnits((short) 1);
        em.persist(slot);

        Booking booking = new Booking();
        booking.setBookingReference("TEST-" + UUID.randomUUID());
        booking.setUser(em.getReference(User.class, 3L));
        booking.setSpa(spa);
        booking.setService(service);
        booking.setSlot(slot);
        booking.setStartTs(slot.getStartTs());
        booking.setEndTs(slot.getEndTs());
        booking.setStatus(status);
        booking.setPaymentMode(PaymentMode.OFFLINE);
        booking.setPriceCents(150_000);
        booking.setDepositCents(0);
        booking.setRemainderCents(150_000);
        booking.setPolicySnapshotJson("{}");
        em.persist(booking);
        em.flush();
        return booking;
    }
}
