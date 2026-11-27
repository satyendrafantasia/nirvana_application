package com.nirvana.application;

import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.gst.GstDocument;
import com.nirvana.application.service.gst.GstDocumentHtml;
import com.nirvana.application.service.gst.GstInvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guest invoices, bills of supply, credit notes and Nirvana's commission invoices. */
@SpringBootTest(properties = {GstInvoiceTest.NIRVANA, "gst.sweep.lookback-days=0"})
@AutoConfigureMockMvc
@Transactional
class GstInvoiceTest {
    /** Nirvana registered in Karnataka (29). */
    static final String NIRVANA = "gst.nirvana.gstin=29AAACN1234A1Z3";
    /** The spa, registered in Uttar Pradesh (09). */
    static final String SPA_GSTIN = "09AABCF1234B1ZA";

    @Autowired GstInvoiceService gst;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;

    long spa;

    @BeforeEach void spa() {
        spa = db.queryForObject("SELECT MIN(s.id) FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id WHERE EXISTS (SELECT 1 FROM booking b WHERE b.spa_id=s.id)", Long.class);
        db.update("UPDATE spa SET gstin=?,legal_name='Fern House Wellness LLP',tax_percent=18,commission_pct=10 WHERE id=?", SPA_GSTIN, spa);
    }

    long booking(long price, long discount, long tax) {
        long template = db.queryForObject("SELECT MIN(id) FROM booking WHERE spa_id=?", Long.class, spa);
        return GstRows.copy(db, "booking", template, Map.of("price_cents", price, "discount_cents", discount, "tax_cents", tax,
                "payment_type", "STANDARD", "payment_mode", "ONLINE", "status", "CONFIRMED", "guest_count", 1, "currency", "INR",
                "booking_reference", GstRows.reference()));
    }

    long paid(long booking, long amount, LocalDateTime captured) {
        long template = db.queryForObject("SELECT MIN(id) FROM payment", Long.class);
        var changes = new HashMap<String, Object>(Map.of("booking_id", booking, "payment_status", "CAPTURED", "amount_cents", amount,
                "refunded_cents", 0, "captured_at", captured, "currency", "INR"));
        changes.put("refunded_at", null);
        changes.put("user_id", db.queryForObject("SELECT user_id FROM booking WHERE id=?", Long.class, booking));
        return GstRows.copy(db, "payment", template, changes);
    }

    void refund(long payment, int cumulative, int amount, LocalDateTime when) {
        db.update("INSERT INTO booking_refund_posting(payment_id,cumulative_cents,amount_cents,recorded_at) VALUES(?,?,?,?)",
                payment, cumulative, amount, when);
    }

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------ tax invoices

    @Test void aRegisteredSpaIssuesATaxInvoiceWithCgstAndSgst() {
        // 2,500.00 less a 100.00 discount is 2,400.00 taxable; 18% is 432.00, so the guest paid 2,832.00.
        long b = booking(250000, 10000, 43200);
        paid(b, 283200, now());
        GstDocument d = gst.issueForBooking(b).orElseThrow();

        assertEquals(GstDocument.TAX_INVOICE, d.docType());
        assertTrue(d.documentNumber().matches("S" + spa + "/\\d{4}/\\d{5,6}"), d.documentNumber());
        assertTrue(d.documentNumber().length() <= 16);
        assertEquals("SPA:" + spa, d.issuer());
        assertEquals("Fern House Wellness LLP", d.supplierName());
        assertEquals(SPA_GSTIN, d.supplierGstin());
        assertEquals("09", d.supplierStateCode());
        assertEquals("09", d.placeOfSupplyCode(), "a treatment is supplied where the spa is");
        assertEquals("Uttar Pradesh", d.placeOfSupply());
        assertEquals("999722", d.sacCode());
        assertEquals(1800, d.rateBps());
        assertEquals(250000, d.grossCents());
        assertEquals(10000, d.discountCents());
        assertEquals(240000, d.taxableCents());
        assertEquals(21600, d.cgstCents());
        assertEquals(21600, d.sgstCents());
        assertEquals(0, d.igstCents());
        assertEquals(283200, d.totalCents(), "the invoice adds up to what the guest paid");
        assertNotNull(d.recipientName());

        assertEquals(d.id(), gst.issueForBooking(b).orElseThrow().id(), "issued once");
        // The next invoice takes the next number in the spa's series.
        long b2 = booking(100000, 0, 18000);
        paid(b2, 118000, now());
        GstDocument d2 = gst.issueForBooking(b2).orElseThrow();
        assertEquals(d.sequenceNumber() + 1, d2.sequenceNumber());
        assertEquals(d.financialYear(), d2.financialYear());
    }

    @Autowired jakarta.persistence.EntityManager em;
    @Autowired com.nirvana.application.service.InvoiceService invoices;

    @Test void paymentCaptureIssuesTheGstInvoiceAndThePdfCarriesIt() throws Exception {
        long b = booking(250000, 10000, 43200);
        long p = paid(b, 283200, now());
        db.update("UPDATE payment SET payment_status='INIT' WHERE id=?", p);
        // As payment capture does: the payment is marked captured in the JPA session, not yet written.
        em.find(com.nirvana.application.model.Payment.class, p).setPaymentStatus(com.nirvana.application.model.enums.PaymentStatus.CAPTURED);
        var receipt = invoices.generateInvoiceForBooking(b);
        GstDocument d = gst.bySource("BOOKING:" + b).orElseThrow();
        assertEquals(d.documentNumber(), receipt.invoiceNumber(), "the receipt and the GST invoice share one number");
        assertEquals("Tax Invoice", receipt.documentTitle());
        assertEquals(SPA_GSTIN, receipt.supplierGstin());
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(com.nirvana.application.service.InvoicePdf.render(receipt))) {
            String t = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            for (String expected : new String[]{"TAX INVOICE", d.documentNumber(), "GSTIN " + SPA_GSTIN, "Fern House Wellness LLP",
                    "SAC 999722", "CGST (9%)", "SGST (9%)", "INR 216.00"})
                assertTrue(t.contains(expected), "missing \"" + expected + "\" in:\n" + t);
        }
    }

    @Test void theServiceCanSetItsOwnSacAndAnOddPaisaGoesToCgst() {
        long b = booking(100001, 0, 18000);
        long service = db.queryForObject("SELECT service_id FROM booking WHERE id=?", Long.class, b);
        db.update("UPDATE service SET sac_code='999721' WHERE id=?", service);
        paid(b, 118001, now());
        GstDocument d = gst.issueForBooking(b).orElseThrow();
        assertEquals("999721", d.sacCode());
        // 18% of 1,000.01 is 180.0018, charged as 180.00: 90.00 + 90.00.
        assertEquals(9000, d.cgstCents());
        assertEquals(9000, d.sgstCents());
        long b2 = booking(10050, 0, 1809);
        paid(b2, 11859, now());
        GstDocument odd = gst.issueForBooking(b2).orElseThrow();
        assertEquals(905, odd.cgstCents());
        assertEquals(904, odd.sgstCents());
        assertEquals(11859, odd.totalCents());
    }

    @Test void aSpaWithoutAGstinIssuesABillOfSupply() {
        db.update("UPDATE spa SET gstin=NULL WHERE id=?", spa);
        long b = booking(250000, 0, 0);
        paid(b, 250000, now());
        GstDocument d = gst.issueForBooking(b).orElseThrow();
        assertEquals(GstDocument.BILL_OF_SUPPLY, d.docType());
        assertTrue(d.documentNumber().startsWith("B" + spa + "/"), d.documentNumber());
        assertEquals(0, d.rateBps());
        assertEquals(0, d.taxCents());
        assertEquals(250000, d.taxableCents());
        assertEquals(250000, d.totalCents());
        assertNull(d.supplierGstin());
        String html = GstDocumentHtml.render(d, null);
        assertTrue(html.contains("Bill of Supply"));
        assertTrue(html.contains("Not registered"));
        assertFalse(html.contains("CGST"), "no GST lines on a bill of supply");
    }

    @Test void aWalletFundedAppointmentGetsItsInvoiceAndOneCreditForTheReturnedTender() {
        long b = booking(10000, 0, 1800);
        db.update("UPDATE booking SET remainder_cents=0 WHERE id=?", b);
        long user = db.queryForObject("SELECT user_id FROM booking WHERE id=?", Long.class, b);
        db.update("INSERT INTO booking_wallet_balance(user_id,currency,balance_cents) VALUES(?,'INR',0) ON DUPLICATE KEY UPDATE user_id=user_id", user);
        db.update("INSERT INTO booking_wallet_spend(booking_id,user_id,currency,amount_cents) VALUES(?,?,'INR',11800)", b,user);
        assertEquals(11800, gst.issueForBooking(b).orElseThrow().totalCents());
        db.update("UPDATE booking_wallet_spend SET returned_at=UTC_TIMESTAMP(), returned_cents=5900 WHERE booking_id=?", b);
        assertEquals(5900, gst.creditBookingRefunds(b).get(0).totalCents());
        assertTrue(gst.creditBookingRefunds(b).isEmpty());
    }

    @Test void anUnpaidBookingHasNoInvoiceYet() {
        long b = booking(250000, 0, 45000);
        assertTrue(gst.issueForBooking(b).isEmpty());
        db.update("UPDATE booking SET payment_mode='OFFLINE',status='COMPLETED' WHERE id=?", b);
        assertTrue(gst.issueForBooking(b).isPresent(), "a completed pay-at-spa booking is invoiced");
    }

    @Test void aPackageInvoiceTakesTheTaxOutOfThePrice() {
        long template = db.queryForObject("SELECT MIN(id) FROM user_spa_package_subscription", Long.class);
        long sub = GstRows.copy(db, "user_spa_package_subscription", template, Map.of("spa_id", spa, "price_paid", new BigDecimal("2950.00"),
                "payment_status", "SUCCESS", "currency", "INR"));
        GstDocument d = gst.issueForPackage(sub).orElseThrow();
        assertEquals(GstDocument.TAX_INVOICE, d.docType());
        assertEquals(sub, d.subscriptionId());
        assertEquals(250000, d.taxableCents());
        assertEquals(22500, d.cgstCents());
        assertEquals(22500, d.sgstCents());
        assertEquals(295000, d.totalCents());

        db.update("INSERT INTO spa_package_refund_posting(subscription_id,cumulative_cents,amount_cents,recorded_at) VALUES(?,?,?,?)",
                sub, 295000, 295000, now());
        var notes = gst.creditPackageRefunds(sub);
        assertEquals(1, notes.size());
        assertEquals(295000, notes.get(0).totalCents());
        assertEquals(45000, notes.get(0).taxCents());
    }

    // ------------------------------------------------------------------ credit notes

    @Test void eachRefundGetsACreditNoteAgainstTheInvoice() {
        long b = booking(250000, 10000, 43200);
        long p = paid(b, 283200, now());
        GstDocument invoice = gst.issueForBooking(b).orElseThrow();

        // Half refunded: half the taxable value and half the tax are reversed.
        refund(p, 141600, 141600, now());
        List<GstDocument> first = gst.creditBookingRefunds(b);
        assertEquals(1, first.size());
        GstDocument note = first.get(0);
        assertEquals(GstDocument.CREDIT_NOTE, note.docType());
        assertEquals(invoice.id(), note.originalDocumentId());
        assertTrue(note.documentNumber().startsWith("C" + spa + "/"), note.documentNumber());
        assertEquals(141600, note.totalCents());
        assertEquals(120000, note.taxableCents());
        assertEquals(10800, note.cgstCents());
        assertEquals(10800, note.sgstCents());
        assertEquals(invoice.supplierGstin(), note.supplierGstin());
        assertTrue(note.notes().contains(invoice.documentNumber()));
        assertTrue(gst.creditBookingRefunds(b).isEmpty(), "a refund is credited once");

        // A later refund larger than what is left only credits the rest of the invoice.
        refund(p, 300000, 158400, now());
        List<GstDocument> second = gst.creditBookingRefunds(b);
        assertEquals(1, second.size());
        assertEquals(141600, second.get(0).totalCents());
        assertEquals(note.sequenceNumber() + 1, second.get(0).sequenceNumber());
        long credited = gst.forBooking(b).stream().filter(d -> GstDocument.CREDIT_NOTE.equals(d.docType()))
                .mapToLong(GstDocument::totalCents).sum();
        assertEquals(invoice.totalCents(), credited);
        long creditedTax = gst.forBooking(b).stream().filter(d -> GstDocument.CREDIT_NOTE.equals(d.docType()))
                .mapToLong(GstDocument::taxCents).sum();
        assertEquals(invoice.taxCents(), creditedTax, "a full refund reverses all the tax");

        String html = GstDocumentHtml.render(note, invoice);
        assertTrue(html.contains("Credit Note"));
        assertTrue(html.contains(invoice.documentNumber()));
        assertTrue(html.contains("CGST @ 9%"));
    }

    @Test void anAmountReturnedAtTheDeskIsCreditedToo() {
        long b = booking(100000, 0, 18000);
        db.update("UPDATE booking SET payment_mode='OFFLINE' WHERE id=?", b);
        long staff = db.queryForObject("SELECT m.user_id FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id WHERE s.id=?", Long.class, spa);
        db.update("INSERT INTO spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method,returned_at,returned_by) VALUES(?,?,?,?,?,?,?,?,?)",
                b, spa, staff, now(), 118000, "INR", "CASH", now(), staff);
        var notes = gst.creditBookingRefunds(b);
        assertEquals(1, notes.size());
        assertEquals(118000, notes.get(0).totalCents());
        assertEquals(18000, notes.get(0).taxCents());
    }

    // ------------------------------------------------------------------ Nirvana's commission

    @Test void nirvanaInvoicesTheSpaForCommissionPlusGst() {
        YearMonth month = YearMonth.of(2020, 1);
        LocalDateTime mid = LocalDateTime.of(2020, 1, 15, 6, 0);
        long b = booking(250000, 10000, 43200);
        long p = paid(b, 283200, mid);
        refund(p, 83200, 83200, mid.plusDays(1));
        // Captured before the month began in India (31 Dec, 23:00 IST) is not counted.
        paid(booking(100000, 0, 18000), 118000, LocalDateTime.of(2019, 12, 31, 17, 30));

        GstDocument d = gst.issueCommission(spa, month).orElseThrow();
        assertEquals(GstDocument.COMMISSION_INVOICE, d.docType());
        assertEquals("NIRVANA", d.issuer());
        assertTrue(d.documentNumber().matches("NV/\\d{4}/\\d{6}"), d.documentNumber());
        assertEquals("29AAACN1234A1Z3", d.supplierGstin());
        assertEquals(SPA_GSTIN, d.recipientGstin());
        assertEquals(200000, d.commissionBaseCents(), "online collections less refunds");
        assertEquals(20000, d.taxableCents(), "10% commission");
        // Nirvana is in Karnataka and the spa in Uttar Pradesh: IGST.
        assertEquals(0, d.cgstCents());
        assertEquals(3600, d.igstCents());
        assertEquals(23600, d.totalCents());
        assertEquals("998599", d.sacCode());
        assertEquals(month.atDay(1), d.periodStart());
        assertEquals(month.atEndOfMonth(), d.periodEnd());
        assertEquals(d.id(), gst.issueCommission(spa, month).orElseThrow().id(), "issued once per month");

        // A spa in Nirvana's own state is charged CGST and SGST.
        long b2 = booking(100000, 0, 18000);
        paid(b2, 118000, LocalDateTime.of(2020, 2, 10, 6, 0));
        db.update("UPDATE spa SET gstin=? WHERE id=?", karnataka(), spa);
        GstDocument feb = gst.issueCommission(spa, YearMonth.of(2020, 2)).orElseThrow();
        assertEquals(11800, feb.taxableCents());
        assertEquals(1062, feb.cgstCents());
        assertEquals(1062, feb.sgstCents());
        assertEquals(0, feb.igstCents());
        assertEquals(feb.sequenceNumber(), d.sequenceNumber() + 1);

        assertTrue(gst.issueCommission(spa, YearMonth.of(2020, 3)).isEmpty(), "no collections, no commission");
        assertThrows(IllegalArgumentException.class, () -> gst.issueCommission(spa, YearMonth.now(java.time.ZoneId.of("Asia/Kolkata"))));
    }

    /** A valid Karnataka GSTIN for the spa. */
    static String karnataka() {
        String first = "29AABCF1234B1Z";
        for (char c : "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()) {
            if (com.nirvana.application.service.gst.GstMath.normalizeGstin(first + c) != null) return first + c;
        }
        throw new AssertionError();
    }

    // ------------------------------------------------------------------ who may read what

    UsernamePasswordAuthenticationToken as(long user, String role) {
        var p = new UserPrincipal(user, "gst", "gst@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test void theGuestAndTheSpaMayOpenTheInvoiceButNoOneElse() throws Exception {
        long b = booking(250000, 10000, 43200);
        paid(b, 283200, now());
        long guest = db.queryForObject("SELECT user_id FROM booking WHERE id=?", Long.class, b);
        long owner = db.queryForObject("SELECT m.user_id FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id WHERE s.id=?", Long.class, spa);
        long stranger = db.queryForObject("SELECT MIN(id) FROM app_user WHERE id NOT IN (?,?)", Long.class, guest, owner);

        String list = mvc.perform(get("/api/bookings/" + b + "/gst-documents").with(authentication(as(guest, "ROLE_USER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains("TAX_INVOICE"), list);
        long id = gst.forBooking(b).get(0).id();
        String html = mvc.perform(get("/api/gst-documents/" + id + "/html").with(authentication(as(guest, "ROLE_USER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Tax Invoice"));
        assertTrue(html.contains(SPA_GSTIN));
        assertTrue(html.contains("999722"));
        assertTrue(html.contains("CGST @ 9%"));
        assertTrue(html.contains("Rupees Two Thousand Eight Hundred Thirty Two Only"), html);
        mvc.perform(get("/api/gst-documents/" + id + "/html").with(authentication(as(owner, "ROLE_SPA_OWNER")))).andExpect(status().isOk());
        mvc.perform(get("/api/gst-documents/" + id + "/html").with(authentication(as(stranger, "ROLE_USER")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/" + b + "/gst-documents").with(authentication(as(stranger, "ROLE_USER")))).andExpect(status().is4xxClientError());

        String owned = mvc.perform(get("/api/owner/spas/" + spa + "/gst-documents").with(authentication(as(owner, "ROLE_SPA_OWNER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(owned.contains(gst.forBooking(b).get(0).documentNumber()));
        mvc.perform(get("/api/owner/spas/" + spa + "/gst-documents").with(authentication(as(guest, "ROLE_USER")))).andExpect(status().isForbidden());

        // Nirvana's commission invoice is for the owner, not the guest.
        paid(booking(100000, 0, 18000), 118000, LocalDateTime.of(2020, 4, 10, 6, 0));
        mvc.perform(post("/api/owner/spas/" + spa + "/commission-invoices/2020-04").with(authentication(as(owner, "ROLE_SPA_OWNER"))))
                .andExpect(status().isOk());
        long commission = gst.bySource("COMMISSION:" + spa + ":2020-04").orElseThrow().id();
        mvc.perform(get("/api/gst-documents/" + commission + "/html").with(authentication(as(owner, "ROLE_SPA_OWNER")))).andExpect(status().isOk());
        mvc.perform(get("/api/gst-documents/" + commission + "/html").with(authentication(as(guest, "ROLE_USER")))).andExpect(status().isNotFound());
        mvc.perform(post("/api/owner/spas/" + spa + "/commission-invoices/2020-05").with(authentication(as(owner, "ROLE_SPA_OWNER"))))
                .andExpect(status().isNoContent());
    }
}
