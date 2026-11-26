package com.nirvana.application.service.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The arithmetic and formats of an Indian GST document. Amounts are in paise; rates are in basis
 * points (1800 = 18%). Nothing here touches the database.
 */
public final class GstMath {
    private GstMath() {}

    /** Tax on an amount that excludes GST, rounded to the nearest paisa (half up). */
    public static long taxOnExclusive(long taxableCents, int rateBps) {
        if (taxableCents <= 0 || rateBps <= 0) return 0;
        return BigDecimal.valueOf(taxableCents).multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The taxable value inside an amount that already includes GST at this rate. */
    public static long taxableFromInclusive(long grossCents, int rateBps) {
        if (grossCents <= 0 || rateBps <= 0) return Math.max(0, grossCents);
        return BigDecimal.valueOf(grossCents).multiply(BigDecimal.valueOf(10_000))
                .divide(BigDecimal.valueOf(10_000L + rateBps), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** A rate given as a percentage, such as 18 or 12.5, in basis points. */
    public static int bps(BigDecimal percent) {
        return percent == null ? 0 : percent.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** "18%" or "2.5%". */
    public static String percent(int bps) {
        return BigDecimal.valueOf(bps).movePointLeft(2).stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * Total tax split by where the supply is. Within one state it is half CGST and half SGST (any odd
     * paisa goes to CGST, so the halves always add back to the total); across states it is all IGST.
     */
    public record Split(long cgst, long sgst, long igst) {
        public long total() { return cgst + sgst + igst; }
    }

    public static Split split(long taxCents, boolean intraState) {
        if (taxCents <= 0) return new Split(0, 0, 0);
        if (!intraState) return new Split(0, 0, taxCents);
        long sgst = taxCents / 2;
        return new Split(taxCents - sgst, sgst, 0);
    }

    /** The share of an original amount that a refund of {@code partCents} out of {@code wholeCents} reverses. */
    public static long proportion(long amountCents, long partCents, long wholeCents) {
        if (wholeCents <= 0 || partCents <= 0) return 0;
        if (partCents >= wholeCents) return amountCents;
        return BigDecimal.valueOf(amountCents).multiply(BigDecimal.valueOf(partCents))
                .divide(BigDecimal.valueOf(wholeCents), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** India's financial year, April to March: 1 Apr 2026 to 31 Mar 2027 is "2026-27". */
    public static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    /** Rule 46(b): at most 16 characters of letters, digits, hyphen and slash, unique in a financial year. */
    public static final int MAX_NUMBER_LENGTH = 16;
    private static final Pattern NUMBER = Pattern.compile("[A-Za-z0-9/-]{1,16}");

    /**
     * A document number such as {@code S12/2627/000123}: the series letter and issuer code, the
     * financial year without its century, and the running number. The running number is padded to six
     * digits where that fits in 16 characters, and is never cut short.
     */
    public static String documentNumber(String prefix, String financialYear, long sequence) {
        String fy = financialYear.replace("-", "").substring(2);
        String head = prefix + "/" + fy + "/";
        String digits = Long.toString(sequence);
        int room = MAX_NUMBER_LENGTH - head.length();
        if (digits.length() > room) {
            throw new IllegalStateException("Document number " + head + digits + " is longer than 16 characters");
        }
        int pad = Math.min(6, room);
        String number = head + "0".repeat(Math.max(0, pad - digits.length())) + digits;
        if (!NUMBER.matcher(number).matches()) throw new IllegalStateException("Invalid document number " + number);
        return number;
    }

    // ---- GSTIN and state codes.

    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** A well-formed GSTIN with a correct check character, upper-cased; null when blank or invalid. */
    public static String normalizeGstin(String raw) {
        if (raw == null) return null;
        String g = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!GSTIN.matcher(g).matches() || !STATE_NAMES.containsKey(g.substring(0, 2))) return null;
        return g.charAt(14) == checkChar(g.substring(0, 14)) ? g : null;
    }

    static char checkChar(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int v = CHARSET.indexOf(first14.charAt(i)) * (i % 2 == 0 ? 1 : 2);
            sum += v / 36 + v % 36;
        }
        return CHARSET.charAt((36 - sum % 36) % 36);
    }

    /** The GST state code for a state's name (or a code given as is), or null when unknown. */
    public static String stateCode(String state) {
        if (state == null || state.isBlank()) return null;
        String s = state.trim();
        if (s.matches("\\d{1,2}")) {
            String code = s.length() == 1 ? "0" + s : s;
            return STATE_NAMES.containsKey(code) ? code : null;
        }
        return CODES.get(key(s));
    }

    public static String stateName(String code) {
        return code == null ? null : STATE_NAMES.get(code);
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z]", "");
    }

    /** CBIC's state code list. */
    private static final Map<String, String> STATE_NAMES = Map.ofEntries(
            Map.entry("01", "Jammu and Kashmir"), Map.entry("02", "Himachal Pradesh"), Map.entry("03", "Punjab"),
            Map.entry("04", "Chandigarh"), Map.entry("05", "Uttarakhand"), Map.entry("06", "Haryana"),
            Map.entry("07", "Delhi"), Map.entry("08", "Rajasthan"), Map.entry("09", "Uttar Pradesh"),
            Map.entry("10", "Bihar"), Map.entry("11", "Sikkim"), Map.entry("12", "Arunachal Pradesh"),
            Map.entry("13", "Nagaland"), Map.entry("14", "Manipur"), Map.entry("15", "Mizoram"),
            Map.entry("16", "Tripura"), Map.entry("17", "Meghalaya"), Map.entry("18", "Assam"),
            Map.entry("19", "West Bengal"), Map.entry("20", "Jharkhand"), Map.entry("21", "Odisha"),
            Map.entry("22", "Chhattisgarh"), Map.entry("23", "Madhya Pradesh"), Map.entry("24", "Gujarat"),
            Map.entry("26", "Dadra and Nagar Haveli and Daman and Diu"), Map.entry("27", "Maharashtra"),
            Map.entry("29", "Karnataka"), Map.entry("30", "Goa"), Map.entry("31", "Lakshadweep"),
            Map.entry("32", "Kerala"), Map.entry("33", "Tamil Nadu"), Map.entry("34", "Puducherry"),
            Map.entry("35", "Andaman and Nicobar Islands"), Map.entry("36", "Telangana"),
            Map.entry("37", "Andhra Pradesh"), Map.entry("38", "Ladakh"), Map.entry("97", "Other Territory"));

    private static final Map<String, String> CODES;
    static {
        var m = new java.util.HashMap<String, String>();
        STATE_NAMES.forEach((code, name) -> m.put(key(name), code));
        // Common spellings and older names.
        m.put(key("Jammu & Kashmir"), "01");
        m.put(key("Orissa"), "21");
        m.put(key("Pondicherry"), "34");
        m.put(key("Uttaranchal"), "05");
        m.put(key("NCT of Delhi"), "07");
        m.put(key("New Delhi"), "07");
        m.put(key("Daman and Diu"), "26");
        m.put(key("Dadra and Nagar Haveli"), "26");
        m.put(key("Andaman and Nicobar"), "35");
        CODES = Map.copyOf(m);
    }
}
