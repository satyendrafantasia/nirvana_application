package com.nirvana.application.service.addons;

import com.nirvana.application.model.Spa;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Add-ons and upgrades a spa sells with a treatment ("+30 min", hot stones, aromatherapy oil).
 *
 * <p>The server alone prices them: a booking names add-on ids, and {@link #select} checks each one
 * belongs to the spa, is active and applies to the treatment. Add-ons are priced per guest, like
 * the treatment. Their extra minutes lengthen the appointment once, however many guests there are.
 * The chosen add-ons are kept on the booking as they were ({@code booking_addon}), and
 * {@code booking.price_cents} already includes them, so tax, payments, refunds, payouts and the
 * desk balance include them with no further work.</p>
 */
@Component
@RequiredArgsConstructor
public class BookingAddOns {
    public static final int MAX_PER_BOOKING = 10;
    /** The most add-on time one appointment can take on. */
    public static final int MAX_EXTRA_MINUTES = 240;

    private final NamedParameterJdbcTemplate db;
    private final SpaRepository spas;
    private final ServiceRepository services;

    public record AddOn(long id, long spaId, String name, String description, int priceCents, String currency,
                        int extraMinutes, boolean active, int sortOrder, List<Long> serviceIds, long version) {
        /** No services listed means every treatment at the spa. */
        public boolean appliesTo(long serviceId) {
            return serviceIds.isEmpty() || serviceIds.contains(serviceId);
        }
    }

    /** One chosen add-on: unit price times guests. */
    public record Line(Long addOnId, String name, int unitPriceCents, int quantity, int amountCents,
                       String currency, int extraMinutes) {
    }

    public record Selection(List<Line> lines, int totalCents, int extraMinutes) {
        public static final Selection NONE = new Selection(List.of(), 0, 0);

        public static Selection of(List<Line> lines) {
            int total = 0, minutes = 0;
            for (Line line : lines) {
                total = Math.addExact(total, line.amountCents());
                minutes += line.extraMinutes();
            }
            return new Selection(List.copyOf(lines), total, minutes);
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    public record Input(String name, String description, int priceCents, String currency, int extraMinutes,
                        boolean active, int sortOrder, Collection<Long> serviceIds, Long version) {
    }

    // ---------------------------------------------------------------- reading

    private static final String COLUMNS = "a.id,a.spa_id,a.name,a.description,a.price_cents,a.currency,a.extra_minutes,a.is_active,a.sort_order,a.version";

    /** Every add-on of the spa, active or not, in menu order. */
    public List<AddOn> forSpa(long spaId) {
        return load("WHERE a.spa_id=:spa ORDER BY a.sort_order,a.name,a.id", Map.of("spa", spaId));
    }

    /** What a guest may add to this treatment. */
    public List<AddOn> bookable(long spaId, long serviceId) {
        return forSpa(spaId).stream().filter(a -> a.active() && a.appliesTo(serviceId)).toList();
    }

    public Optional<AddOn> find(long spaId, long id) {
        return load("WHERE a.spa_id=:spa AND a.id=:id", Map.of("spa", spaId, "id", id)).stream().findFirst();
    }

    private List<AddOn> load(String where, Map<String, ?> args) {
        var rows = db.queryForList("SELECT " + COLUMNS + " FROM spa_addon a " + where, args);
        if (rows.isEmpty()) return List.of();
        Map<Long, List<Long>> scope = new HashMap<>();
        List<Long> ids = rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        db.query("SELECT addon_id,service_id FROM spa_addon_service WHERE addon_id IN (:ids) ORDER BY service_id",
                Map.of("ids", ids), rs -> {
                    scope.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                });
        return rows.stream().map(r -> {
            long id = ((Number) r.get("id")).longValue();
            return new AddOn(id, ((Number) r.get("spa_id")).longValue(), (String) r.get("name"), (String) r.get("description"),
                    ((Number) r.get("price_cents")).intValue(), (String) r.get("currency"), ((Number) r.get("extra_minutes")).intValue(),
                    truthy(r.get("is_active")), ((Number) r.get("sort_order")).intValue(),
                    List.copyOf(scope.getOrDefault(id, List.of())), ((Number) r.get("version")).longValue());
        }).toList();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value instanceof byte[] bytes) return bytes.length > 0 && bytes[0] != 0;
        return false;
    }

    // ---------------------------------------------------------------- choosing at checkout

    /** Loads the spa and treatment, for callers that only have ids (the slot list). */
    public Selection select(long spaId, long serviceId, Collection<Long> ids, int guests) {
        if (ids == null || ids.isEmpty()) return Selection.NONE;
        Spa spa = spas.findById(spaId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Spa not found"));
        var service = services.findByIdAndSpa_Id(serviceId, spaId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Treatment not found"));
        return select(spa, service, ids, guests);
    }

    /**
     * Checks and prices the add-ons a guest chose. Never trusts a price from the client.
     *
     * @throws ResponseStatusException 400 for an id that isn't this spa's, is inactive or doesn't apply
     */
    public Selection select(Spa spa, com.nirvana.application.model.Service service, Collection<Long> ids, int guests) {
        if (ids == null || ids.isEmpty()) return Selection.NONE;
        if (ids.stream().anyMatch(Objects::isNull)) throw rejected("Choose add-ons from this spa's menu");
        var wanted = new LinkedHashSet<>(ids);
        if (wanted.size() > MAX_PER_BOOKING) throw rejected("Choose up to " + MAX_PER_BOOKING + " add-ons");
        String currency = service.getCurrency() != null ? service.getCurrency() : spa.getDefaultCurrency();
        Map<Long, AddOn> found = new HashMap<>();
        for (AddOn a : load("WHERE a.spa_id=:spa AND a.id IN (:ids)", Map.of("spa", spa.getId(), "ids", wanted))) found.put(a.id(), a);
        List<AddOn> chosen = new ArrayList<>();
        for (Long id : wanted) {
            AddOn a = found.get(id);
            if (a == null) throw rejected("That add-on isn't offered by this spa");
            if (!a.active()) throw rejected(a.name() + " is no longer available. Remove it to continue.");
            if (!a.appliesTo(service.getId())) throw rejected(a.name() + " can't be added to " + service.getName());
            if (currency != null && !currency.equalsIgnoreCase(a.currency())) throw rejected(a.name() + " is priced in a different currency");
            chosen.add(a);
        }
        chosen.sort(Comparator.comparingInt(AddOn::sortOrder).thenComparing(AddOn::name).thenComparingLong(AddOn::id));
        int quantity = Math.max(1, guests);
        var lines = chosen.stream().map(a -> new Line(a.id(), a.name(), a.priceCents(), quantity,
                Math.multiplyExact(a.priceCents(), quantity), a.currency(), a.extraMinutes())).toList();
        var selection = Selection.of(lines);
        if (selection.extraMinutes() > MAX_EXTRA_MINUTES) throw rejected("These add-ons add more than " + MAX_EXTRA_MINUTES + " minutes");
        return selection;
    }

    /** Keeps the chosen add-ons on the booking as they are now. */
    public void record(long bookingId, Selection selection) {
        for (Line line : selection.lines()) {
            db.update("""
                    INSERT INTO booking_addon(booking_id,addon_id,name,unit_price_cents,quantity,amount_cents,currency,extra_minutes)
                    VALUES(:booking,:addon,:name,:unit,:qty,:amount,:currency,:minutes)""",
                    new MapSqlParameterSource().addValue("booking", bookingId).addValue("addon", line.addOnId())
                            .addValue("name", line.name()).addValue("unit", line.unitPriceCents()).addValue("qty", line.quantity())
                            .addValue("amount", line.amountCents()).addValue("currency", line.currency())
                            .addValue("minutes", line.extraMinutes()));
        }
    }

    // ---------------------------------------------------------------- a booking's add-ons

    public Selection forBooking(long bookingId) {
        return forBookings(List.of(bookingId)).getOrDefault(bookingId, Selection.NONE);
    }

    public Map<Long, Selection> forBookings(Collection<Long> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) return Map.of();
        Map<Long, List<Line>> lines = new HashMap<>();
        db.query("""
                SELECT booking_id,addon_id,name,unit_price_cents,quantity,amount_cents,currency,extra_minutes
                FROM booking_addon WHERE booking_id IN (:ids) ORDER BY booking_id,id""", Map.of("ids", bookingIds), rs -> {
            long addOn = rs.getLong(2);
            Long addOnId = rs.wasNull() ? null : addOn;
            lines.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Line(addOnId,
                    rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getInt(8)));
        });
        Map<Long, Selection> result = new HashMap<>();
        lines.forEach((id, list) -> result.put(id, Selection.of(list)));
        return result;
    }

    /** The extra minutes a booking was sold with, kept when it moves to another time. */
    public int extraMinutes(long bookingId) {
        Integer minutes = db.queryForObject("SELECT COALESCE(SUM(extra_minutes),0) FROM booking_addon WHERE booking_id=:id",
                Map.of("id", bookingId), Integer.class);
        return minutes == null ? 0 : minutes;
    }

    // ---------------------------------------------------------------- the owner's menu

    @Transactional
    public AddOn create(long spaId, Input in) {
        var scope = checkedScope(spaId, in);
        var key = new GeneratedKeyHolder();
        db.update("""
                INSERT INTO spa_addon(spa_id,name,description,price_cents,currency,extra_minutes,is_active,sort_order)
                VALUES(:spa,:name,:description,:price,:currency,:minutes,:active,:sort)""",
                values(spaId, in), key, new String[]{"id"});
        long id = Objects.requireNonNull(key.getKey()).longValue();
        replaceScope(id, scope);
        return find(spaId, id).orElseThrow();
    }

    @Transactional
    public AddOn update(long spaId, long id, Input in) {
        var current = find(spaId, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on not found in this spa"));
        if (in.version() == null || in.version() != current.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This add-on changed; reload before saving");
        var scope = checkedScope(spaId, in);
        var args = values(spaId, in).addValue("id", id).addValue("version", current.version());
        int changed = db.update("""
                UPDATE spa_addon SET name=:name,description=:description,price_cents=:price,currency=:currency,
                  extra_minutes=:minutes,is_active=:active,sort_order=:sort,version=version+1
                WHERE id=:id AND spa_id=:spa AND version=:version""", args);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This add-on changed; reload before saving");
        replaceScope(id, scope);
        return find(spaId, id).orElseThrow();
    }

    /** Past bookings keep their copy of the add-on, so it can go. */
    @Transactional
    public void delete(long spaId, long id) {
        if (db.update("DELETE FROM spa_addon WHERE id=:id AND spa_id=:spa", Map.of("id", id, "spa", spaId)) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Add-on not found in this spa");
    }

    private Set<Long> checkedScope(long spaId, Input in) {
        Set<Long> scope = new TreeSet<>(in.serviceIds() == null ? List.of() : in.serviceIds());
        if (!scope.isEmpty()) {
            Integer owned = db.queryForObject("SELECT COUNT(*) FROM service WHERE spa_id=:spa AND id IN (:ids)",
                    Map.of("spa", spaId, "ids", scope), Integer.class);
            if (owned == null || owned != scope.size()) throw rejected("Treatments must belong to this spa");
        }
        return scope;
    }

    private void replaceScope(long id, Set<Long> scope) {
        db.update("DELETE FROM spa_addon_service WHERE addon_id=:id", Map.of("id", id));
        for (Long service : scope)
            db.update("INSERT INTO spa_addon_service(addon_id,service_id) VALUES(:id,:service)", Map.of("id", id, "service", service));
    }

    private static MapSqlParameterSource values(long spaId, Input in) {
        String description = in.description() == null || in.description().isBlank() ? null : in.description().trim();
        return new MapSqlParameterSource().addValue("spa", spaId).addValue("name", in.name().trim())
                .addValue("description", description).addValue("price", in.priceCents())
                .addValue("currency", in.currency().toUpperCase(Locale.ROOT)).addValue("minutes", in.extraMinutes())
                .addValue("active", in.active()).addValue("sort", in.sortOrder());
    }

    private static ResponseStatusException rejected(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
