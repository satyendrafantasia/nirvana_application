package com.nirvana.application.groups;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seats every guest of a group at one start time: a slot (which fixes the room) and a therapist each.
 *
 * <ul>
 *   <li>No therapist serves two guests.</li>
 *   <li>A room serves one guest, unless the guests sharing it take the same "side by side" service
 *       on the same slot, up to that service's capacity (and the room's capacity when it has one).</li>
 *   <li>A guest's named therapist or therapist label is respected.</li>
 * </ul>
 * Pure logic, so it is unit tested without a database. Groups are at most six guests, so a
 * backtracking search is cheap.
 */
public final class GroupPlanner {
    private GroupPlanner() {}

    /** A therapist free and qualified for one slot. */
    public record Staff(long id, String type) {}

    /** One bookable slot for a guest's service at the start time being tried. */
    public record Option(long slotId, long roomId, Integer roomCapacity, List<Staff> staff) {}

    /**
     * What one guest asked for and could have. {@code sideBySide} is the service's shared-room capacity
     * or null. {@code fixedSlot}/{@code therapistId} pin the guest (a hold being converted, or a named therapist).
     */
    public record Guest(long serviceId, Integer sideBySide, Long therapistId, String therapistType, Long fixedSlot,
                        List<Option> options) {}

    public record Seat(long slotId, long roomId, long therapistId) {}

    /** Seats in guest order, or null when the group does not fit. */
    public static List<Seat> plan(List<Guest> guests) {
        int n = guests.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // Most constrained guests first: named therapists, then fewest choices.
        java.util.Arrays.sort(order, Comparator.<Integer>comparingInt(i -> guests.get(i).therapistId() == null ? 1 : 0)
                .thenComparingInt(i -> choices(guests.get(i))));
        Seat[] seats = new Seat[n];
        if (!search(guests, order, 0, seats, new HashSet<>(), new HashMap<>())) return null;
        return List.of(seats);
    }

    private static int choices(Guest guest) {
        return guest.options().stream().mapToInt(o -> o.staff().size()).sum();
    }

    private record RoomUse(long slotId, long serviceId, int guests) {}

    private static boolean search(List<Guest> guests, Integer[] order, int depth, Seat[] seats,
                                  Set<Long> usedStaff, Map<Long, RoomUse> rooms) {
        if (depth == order.length) return true;
        int index = order[depth];
        Guest guest = guests.get(index);
        for (Option option : guest.options()) {
            if (guest.fixedSlot() != null && guest.fixedSlot() != option.slotId()) continue;
            RoomUse use = rooms.get(option.roomId());
            if (use != null && !canShare(guest, option, use)) continue;
            for (Staff staff : option.staff()) {
                if (usedStaff.contains(staff.id())) continue;
                if (guest.therapistId() != null && guest.therapistId() != staff.id()) continue;
                if (guest.therapistType() != null && !guest.therapistType().isBlank()
                        && !guest.therapistType().equalsIgnoreCase(staff.type())) continue;
                usedStaff.add(staff.id());
                rooms.put(option.roomId(), use == null ? new RoomUse(option.slotId(), guest.serviceId(), 1)
                        : new RoomUse(use.slotId(), use.serviceId(), use.guests() + 1));
                seats[index] = new Seat(option.slotId(), option.roomId(), staff.id());
                if (search(guests, order, depth + 1, seats, usedStaff, rooms)) return true;
                usedStaff.remove(staff.id());
                if (use == null) rooms.remove(option.roomId()); else rooms.put(option.roomId(), use);
                seats[index] = null;
            }
        }
        return false;
    }

    private static boolean canShare(Guest guest, Option option, RoomUse use) {
        if (guest.sideBySide() == null || use.slotId() != option.slotId() || use.serviceId() != guest.serviceId()) return false;
        int together = use.guests() + 1;
        return together <= guest.sideBySide() && (option.roomCapacity() == null || together <= option.roomCapacity());
    }

    /** Splits {@code total} across {@code weights} in proportion, never giving a share more than its weight. */
    public static int[] proRata(int total, int[] weights) {
        int[] shares = new int[weights.length];
        long sum = 0;
        for (int w : weights) sum += w;
        if (total <= 0 || sum <= 0) return shares;
        if (total > sum) throw new IllegalArgumentException("Cannot split more than the order value");
        int given = 0;
        List<double[]> remainders = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            double exact = (double) total * weights[i] / sum;
            shares[i] = (int) Math.floor(exact);
            given += shares[i];
            remainders.add(new double[]{exact - shares[i], i});
        }
        remainders.sort((a, b) -> a[0] != b[0] ? Double.compare(b[0], a[0]) : Double.compare(a[1], b[1]));
        for (int k = 0; given < total; k++) {
            int i = (int) remainders.get(k % remainders.size())[1];
            if (shares[i] < weights[i]) { shares[i]++; given++; }
        }
        return shares;
    }
}
