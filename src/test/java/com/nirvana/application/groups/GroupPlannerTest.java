package com.nirvana.application.groups;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupPlannerTest {
    static GroupPlanner.Staff staff(long id) { return new GroupPlanner.Staff(id, null); }

    static GroupPlanner.Option room(long slot, long room, Integer capacity, long... therapists) {
        return new GroupPlanner.Option(slot, room, capacity, java.util.Arrays.stream(therapists).mapToObj(GroupPlannerTest::staff).toList());
    }

    static GroupPlanner.Guest guest(long service, Integer sideBySide, Long therapist, List<GroupPlanner.Option> options) {
        return new GroupPlanner.Guest(service, sideBySide, therapist, null, null, options);
    }

    @Test void twoGuestsNeedTwoDistinctTherapists() {
        var rooms = List.of(room(10, 1, null, 7), room(11, 2, null, 7));
        assertNull(GroupPlanner.plan(List.of(guest(1, null, null, rooms), guest(1, null, null, rooms))),
                "one therapist cannot serve two guests at the same time");

        var staffed = List.of(room(10, 1, null, 7, 8), room(11, 2, null, 7, 8));
        var seats = GroupPlanner.plan(List.of(guest(1, null, null, staffed), guest(1, null, null, staffed)));
        assertNotNull(seats);
        assertNotEquals(seats.get(0).therapistId(), seats.get(1).therapistId());
        assertNotEquals(seats.get(0).roomId(), seats.get(1).roomId());
    }

    @Test void withoutACouplesRoomEachGuestNeedsARoom() {
        var oneRoom = List.of(room(10, 1, null, 7, 8));
        assertNull(GroupPlanner.plan(List.of(guest(1, null, null, oneRoom), guest(1, null, null, oneRoom))));
    }

    @Test void aSideBySideServiceSharesOneRoomUpToItsCapacity() {
        var couples = List.of(room(10, 1, null, 7, 8, 9));
        var pair = GroupPlanner.plan(List.of(guest(1, 2, null, couples), guest(1, 2, null, couples)));
        assertNotNull(pair);
        assertEquals(pair.get(0).slotId(), pair.get(1).slotId());
        assertNull(GroupPlanner.plan(List.of(guest(1, 2, null, couples), guest(1, 2, null, couples), guest(1, 2, null, couples))),
                "a couples room for two does not take a third guest");
        var smallRoom = List.of(room(10, 1, 2, 7, 8, 9));
        assertNull(GroupPlanner.plan(List.of(guest(1, 3, null, smallRoom), guest(1, 3, null, smallRoom), guest(1, 3, null, smallRoom))),
                "the room's own capacity also caps sharing");
    }

    @Test void differentServicesDoNotShareARoomEvenIfSideBySide() {
        var a = List.of(room(10, 1, null, 7, 8));
        var b = List.of(room(20, 1, null, 7, 8));
        assertNull(GroupPlanner.plan(List.of(guest(1, 2, null, a), guest(2, 2, null, b))));
    }

    @Test void namedTherapistsAreHonouredAndTheSearchBacktracks() {
        // Guest 1 could take therapist 7 first; guest 2 insists on 7, so guest 1 must get 8.
        var rooms = List.of(room(10, 1, null, 7, 8), room(11, 2, null, 7));
        var seats = GroupPlanner.plan(List.of(guest(1, null, null, rooms), guest(1, null, 7L, rooms)));
        assertNotNull(seats);
        assertEquals(7, seats.get(1).therapistId());
        assertEquals(8, seats.get(0).therapistId());
        assertNull(GroupPlanner.plan(List.of(guest(1, null, 9L, rooms), guest(1, null, null, rooms))));
    }

    @Test void sixGuestsAcrossServicesAreSeated() {
        var massage = List.of(room(10, 1, null, 1, 2, 3, 4), room(11, 2, null, 1, 2, 3, 4), room(12, 3, null, 1, 2, 3, 4));
        var facial = List.of(room(20, 4, null, 5, 6), room(21, 5, null, 5, 6), room(22, 6, null, 5, 6));
        var seats = GroupPlanner.plan(List.of(guest(1, null, null, massage), guest(1, null, null, massage), guest(1, null, null, massage),
                guest(2, null, null, facial), guest(2, null, null, facial), guest(1, null, null, massage)));
        assertNull(seats, "four massage guests need four massage rooms; only three exist");
        var seated = GroupPlanner.plan(List.of(guest(1, null, null, massage), guest(1, null, null, massage), guest(1, null, null, massage),
                guest(2, null, null, facial), guest(2, null, null, facial)));
        assertNotNull(seated);
        assertEquals(5, new HashSet<>(seated.stream().map(GroupPlanner.Seat::therapistId).toList()).size());
        assertEquals(5, new HashSet<>(seated.stream().map(GroupPlanner.Seat::roomId).toList()).size());
    }

    @Test void proRataSplitsExactlyWithoutExceedingAShare() {
        int[] shares = GroupPlanner.proRata(1001, new int[]{2000, 1000, 1});
        assertEquals(1001, java.util.Arrays.stream(shares).sum());
        assertTrue(shares[2] <= 1);
        assertArrayEquals(new int[]{0, 0}, GroupPlanner.proRata(0, new int[]{5, 5}));
        assertArrayEquals(new int[]{5, 5}, GroupPlanner.proRata(10, new int[]{5, 5}));
        assertThrows(IllegalArgumentException.class, () -> GroupPlanner.proRata(11, new int[]{5, 5}));
    }
}
