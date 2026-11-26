package com.nirvana.application.service.gst;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out document numbers, one series per issuer, financial year and kind of document.
 *
 * <p>The counter is bumped by a single {@code INSERT ... ON DUPLICATE KEY UPDATE} inside the caller's
 * transaction, the same one that inserts the document. That statement takes an exclusive lock on the
 * counter row (creating it for a new series), so two issues at once queue on it and never share a
 * number; and an issue that rolls back takes its number back with it, so the series has no gaps.
 */
@Component
@RequiredArgsConstructor
public class GstNumbering {
    private final JdbcTemplate db;

    /** Series: I tax invoice, B bill of supply, C credit note. */
    public record Number(String financialYear, char series, long sequence, String documentNumber) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Number next(String issuer, char series, String prefix, String financialYear) {
        // LAST_INSERT_ID(expr) hands the new value back on this connection without a second read.
        db.update("""
                INSERT INTO gst_document_sequence(issuer,financial_year,series,last_number) VALUES(?,?,?,LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE last_number=LAST_INSERT_ID(last_number+1)""",
                issuer, financialYear, String.valueOf(series));
        Long next = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (next == null || next < 1) throw new IllegalStateException("No document number for " + issuer + " " + financialYear);
        return new Number(financialYear, series, next, GstMath.documentNumber(prefix, financialYear, next));
    }

    /** A spa's prefix for a series: S12 for invoices, B12 bills of supply, C12 credit notes. */
    public static String spaPrefix(long spaId, char series) {
        return (series == 'I' ? "S" : String.valueOf(series)) + spaCode(spaId);
    }

    /** The issuer for a spa's own documents. */
    public static String spaIssuer(long spaId) {
        return "SPA:" + spaId;
    }

    /**
     * The spa's part of a document number: its id when that leaves room for five-digit running numbers,
     * else Z and the id in base 36 (decimal ids never contain a Z, so the two never collide).
     */
    public static String spaCode(long spaId) {
        String decimal = Long.toString(spaId);
        return decimal.length() <= 4 ? decimal : "Z" + Long.toString(spaId, 36).toUpperCase();
    }
}
