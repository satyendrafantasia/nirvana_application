package com.nirvana.application.service.payout;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A spa's bank details for payouts. The full account number is used once, to set up the Razorpay Route linked
 * account, and is never stored or logged: Nirvana keeps the holder, IFSC and last four digits.
 */
@Service
@RequiredArgsConstructor
public class PayoutAccountService {
    public static final Set<String> BUSINESS_TYPES = Set.of("individual", "proprietorship", "partnership",
            "private_limited", "public_limited", "llp", "trust", "society", "not_yet_registered");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT = Pattern.compile("^[0-9]{9,18}$");
    private static final Pattern LINKED = Pattern.compile("^acc_[A-Za-z0-9]{6,40}$");

    private final NamedParameterJdbcTemplate db;
    private final PayoutProvider provider;
    private final PayoutService payouts;

    public record BankDetails(String accountHolder, String ifsc, String accountNumber, String businessType) {
        @Override public String toString() { return "BankDetails[holder=" + accountHolder + ", ifsc=" + ifsc + "]"; }
    }

    /** What the owner and admins see: never the account number. */
    public Map<String, Object> view(long spa) {
        var rows = db.queryForList("""
            SELECT account_holder AS accountHolder,ifsc,CONCAT('XXXX',account_last4) AS accountMasked,business_type AS businessType,
              route_account_id AS routeAccountId,route_status AS routeStatus,route_error AS routeError,updated_at AS updatedAt
            FROM spa_payout_account WHERE spa_id=:spa
            """, Map.of("spa", spa));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> save(long spa, BankDetails d, long by) {
        String holder = d.accountHolder() == null ? "" : d.accountHolder().trim().replaceAll("\\s+", " ");
        String ifsc = d.ifsc() == null ? "" : d.ifsc().trim().toUpperCase(Locale.ROOT);
        String number = d.accountNumber() == null ? "" : d.accountNumber().replaceAll("[\\s-]", "");
        String type = d.businessType() == null || d.businessType().isBlank() ? "proprietorship" : d.businessType().trim().toLowerCase(Locale.ROOT);
        if (holder.length() < 3 || holder.length() > 120) throw bad("Enter the account holder's name as the bank has it");
        if (!IFSC.matcher(ifsc).matches()) throw bad("Enter a valid 11-character IFSC, like HDFC0001234");
        if (!ACCOUNT.matcher(number).matches()) throw bad("Enter the account number: 9 to 18 digits");
        if (!BUSINESS_TYPES.contains(type)) throw bad("Choose the type of business");
        var existing = db.queryForList("SELECT route_account_id,route_product_id FROM spa_payout_account WHERE spa_id=:spa", Map.of("spa", spa));
        String account = existing.isEmpty() ? null : (String) existing.get(0).get("route_account_id");
        String product = existing.isEmpty() ? null : (String) existing.get(0).get("route_product_id");
        String status = account == null ? "NOT_LINKED" : "LINKED", error = null;
        if (provider.automatic()) {
            var spaRow = db.queryForMap("""
                SELECT name,COALESCE(NULLIF(TRIM(owner_name),''),name) AS contact,email,phone,address_line1,address_line2,city,state,postal_code
                FROM spa WHERE id=:spa
                """, Map.of("spa", spa));
            var link = provider.link(new PayoutProvider.LinkRequest(spa, account, product, (String) spaRow.get("name"), type,
                    (String) spaRow.get("contact"), (String) spaRow.get("email"), (String) spaRow.get("phone"),
                    (String) spaRow.get("address_line1"), (String) spaRow.get("address_line2"), (String) spaRow.get("city"),
                    (String) spaRow.get("state"), (String) spaRow.get("postal_code"), holder, ifsc, number));
            account = link.accountId();
            product = link.productId();
            error = link.error();
            status = error == null && account != null ? "LINKED" : "FAILED";
        }
        db.update("""
            INSERT INTO spa_payout_account(spa_id,account_holder,ifsc,account_last4,business_type,route_account_id,route_product_id,route_status,route_error,updated_by,updated_at)
            VALUES(:spa,:holder,:ifsc,:last4,:type,:account,:product,:status,:error,:by,:now)
            ON DUPLICATE KEY UPDATE account_holder=VALUES(account_holder),ifsc=VALUES(ifsc),account_last4=VALUES(account_last4),
              business_type=VALUES(business_type),route_account_id=VALUES(route_account_id),route_product_id=VALUES(route_product_id),
              route_status=VALUES(route_status),route_error=VALUES(route_error),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at)
            """, new MapSqlParameterSource().addValue("spa", spa).addValue("holder", holder).addValue("ifsc", ifsc)
                .addValue("last4", number.substring(number.length() - 4)).addValue("type", type).addValue("account", account)
                .addValue("product", product).addValue("status", status).addValue("error", error).addValue("by", by)
                .addValue("now", LocalDateTime.now(ZoneOffset.UTC)));
        if ("LINKED".equals(status)) payouts.sendWaiting();
        return view(spa);
    }

    /** Admin: a linked account set up on the Razorpay dashboard instead of through Nirvana. */
    public Map<String, Object> setLinkedAccount(long spa, String accountId) {
        String id = accountId == null ? "" : accountId.trim();
        if (!LINKED.matcher(id).matches()) throw bad("A Razorpay linked account id looks like acc_XXXXXXXXXXXXXX");
        try {
            int n = db.update("UPDATE spa_payout_account SET route_account_id=:id,route_status='LINKED',route_error=NULL,updated_at=:now WHERE spa_id=:spa",
                    Map.of("id", id, "spa", spa, "now", LocalDateTime.now(ZoneOffset.UTC)));
            if (n != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "The spa hasn't added bank details yet");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That linked account belongs to another spa");
        }
        payouts.sendWaiting();
        return view(spa);
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
