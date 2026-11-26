package com.nirvana.application;

import com.nirvana.application.service.gst.GstMath;
import com.nirvana.application.service.gst.GstNumbering;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The arithmetic and formats of GST documents. */
class GstMathTest {

    @Test void taxOnAnExclusivePriceRoundsToTheNearestPaisa() {
        assertEquals(45000, GstMath.taxOnExclusive(250000, 1800));   // 18% of 2,500.00
        assertEquals(18, GstMath.taxOnExclusive(99, 1800));          // 17.82 paise rounds up
        assertEquals(17, GstMath.taxOnExclusive(97, 1800));          // 17.46 paise rounds down
        assertEquals(9, GstMath.taxOnExclusive(50, 1800));           // exactly 9
        assertEquals(0, GstMath.taxOnExclusive(250000, 0));
        assertEquals(0, GstMath.taxOnExclusive(-5, 1800));
    }

    @Test void withinAStateTheTaxIsHalfCgstAndHalfSgst() {
        var even = GstMath.split(45000, true);
        assertEquals(22500, even.cgst());
        assertEquals(22500, even.sgst());
        assertEquals(0, even.igst());
        // An odd paisa goes to CGST, and the halves always add back to the tax charged.
        var odd = GstMath.split(45001, true);
        assertEquals(22501, odd.cgst());
        assertEquals(22500, odd.sgst());
        assertEquals(45001, odd.total());
        for (long tax = 0; tax < 500; tax++) {
            var s = GstMath.split(tax, true);
            assertEquals(tax, s.total());
            assertTrue(s.cgst() - s.sgst() == 0 || s.cgst() - s.sgst() == 1);
        }
    }

    @Test void acrossStatesTheTaxIsAllIgst() {
        var s = GstMath.split(45001, false);
        assertEquals(0, s.cgst());
        assertEquals(0, s.sgst());
        assertEquals(45001, s.igst());
    }

    @Test void taxIsTakenOutOfAnInclusivePrice() {
        // A package for 2,950.00 including 18% GST is 2,500.00 plus 450.00.
        assertEquals(250000, GstMath.taxableFromInclusive(295000, 1800));
        // 1,000.00 including 18%: 847.457... rounds to 847.46, leaving 152.54 tax.
        assertEquals(84746, GstMath.taxableFromInclusive(100000, 1800));
        assertEquals(100000, GstMath.taxableFromInclusive(100000, 0));
    }

    @Test void aPartRefundReversesItsShareOfTheTax() {
        assertEquals(22500, GstMath.proportion(45000, 147500, 295000)); // half the total, half the tax
        assertEquals(45000, GstMath.proportion(45000, 295000, 295000));
        assertEquals(45000, GstMath.proportion(45000, 999999, 295000)); // never more than all of it
        assertEquals(0, GstMath.proportion(45000, 0, 295000));
    }

    @Test void ratesAndPercentages() {
        assertEquals(1800, GstMath.bps(new BigDecimal("18")));
        assertEquals(250, GstMath.bps(new BigDecimal("2.5")));
        assertEquals("18%", GstMath.percent(1800));
        assertEquals("9%", GstMath.percent(900));
        assertEquals("2.5%", GstMath.percent(250));
    }

    @Test void theFinancialYearRunsAprilToMarch() {
        assertEquals("2025-26", GstMath.financialYear(LocalDate.of(2026, 3, 31)));
        assertEquals("2026-27", GstMath.financialYear(LocalDate.of(2026, 4, 1)));
        assertEquals("2026-27", GstMath.financialYear(LocalDate.of(2026, 11, 27)));
        assertEquals("2099-00", GstMath.financialYear(LocalDate.of(2100, 1, 15)));
    }

    @Test void documentNumbersFitRule46() {
        assertEquals("S12/2627/000123", GstMath.documentNumber(GstNumbering.spaPrefix(12, 'I'), "2026-27", 123));
        assertEquals("C12/2627/000001", GstMath.documentNumber(GstNumbering.spaPrefix(12, 'C'), "2026-27", 1));
        assertEquals("NV/2627/000042", GstMath.documentNumber("NV", "2026-27", 42));
        // A four-digit spa id leaves room for five digits; a longer id is written in base 36 behind a Z.
        assertEquals("S9999/2627/00001", GstMath.documentNumber(GstNumbering.spaPrefix(9999, 'I'), "2026-27", 1));
        assertEquals("SZ7PS/2627/00001", GstMath.documentNumber(GstNumbering.spaPrefix(10_000, 'I'), "2026-27", 1));
        assertEquals("SZQGLJ/2627/0001", GstMath.documentNumber(GstNumbering.spaPrefix(1_234_567, 'I'), "2026-27", 1));
        // The running number is never cut short: past the room it refuses rather than reuse a number.
        assertEquals("S12/2627/1234567", GstMath.documentNumber("S12", "2026-27", 1_234_567));
        // Room for 9,999 a year at the least, for any spa id up to ZZZZ in base 36 (1,679,615).
        for (long spa : new long[]{1, 12, 999, 9999, 10_000, 1_234_567, 1_679_615}) {
            for (char series : new char[]{'I', 'B', 'C'}) {
                String n = GstMath.documentNumber(GstNumbering.spaPrefix(spa, series), "2026-27", 9_999);
                assertTrue(n.length() <= 16, n);
                assertTrue(n.matches("[A-Z0-9/-]+"), n);
            }
        }
        assertThrows(IllegalStateException.class, () -> GstMath.documentNumber("S12", "2026-27", 100_000_000L));
    }

    @Test void gstinsAreCheckedAgainstTheirCheckCharacter() {
        assertEquals("27AAPFU0939F1ZV", GstMath.normalizeGstin(" 27aapfu0939f1zv "));
        assertNull(GstMath.normalizeGstin("27AAPFU0939F1ZW"), "wrong check character");
        assertNull(GstMath.normalizeGstin("99AAPFU0939F1ZV"), "no such state");
        assertNull(GstMath.normalizeGstin("27AAPFU0939F1Z"), "too short");
        assertNull(GstMath.normalizeGstin(""));
        assertNull(GstMath.normalizeGstin(null));
    }

    @Test void statesAreNamedOrCoded() {
        assertEquals("09", GstMath.stateCode("Uttar Pradesh"));
        assertEquals("09", GstMath.stateCode(" uttar pradesh "));
        assertEquals("07", GstMath.stateCode("New Delhi"));
        assertEquals("01", GstMath.stateCode("Jammu & Kashmir"));
        assertEquals("21", GstMath.stateCode("Orissa"));
        assertEquals("29", GstMath.stateCode("29"));
        assertEquals("09", GstMath.stateCode("9"));
        assertNull(GstMath.stateCode("Atlantis"));
        assertEquals("Karnataka", GstMath.stateName("29"));
    }
}
