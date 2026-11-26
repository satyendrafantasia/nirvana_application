# GST invoices

Nirvana is a marketplace: the **spa** supplies the treatment, so the spa issues the guest's invoice.
Nirvana separately invoices the spa for its commission. Code: `service/gst/`, endpoints in
`controller/GstDocumentController.java`, tables in `V71__gst_invoices.sql`.

## Documents

| Document | Issued by | When | Number |
|---|---|---|---|
| Tax invoice | Spa with a valid GSTIN | A booking is paid online, paid at the desk, or a pay-at-spa booking is completed; a spa package is bought | `S12/2627/000123` |
| Bill of supply | Spa without a GSTIN | Same | `B12/2627/000001` |
| Credit note | The spa | Each refund: online, wallet credit, or money returned at the desk | `C12/2627/000001` |
| Commission invoice | Nirvana | Monthly, for a month that has ended | `NV/2627/000001` |

- Numbers follow Rule 46(b): at most 16 characters, one gap-free series per issuer, series and
  financial year (April to March). `12` is the spa's id; ids over 9999 are written in base 36 after a
  `Z`. The counter is bumped in the same transaction that saves the document, so concurrent issues
  never share a number and a failed issue leaves no gap.
- Every document is a frozen snapshot: names, GSTINs, addresses and amounts are copied in at issue.
- Bookings paid with package sessions get no invoice; the package purchase was invoiced.

## Tax

- **Bookings**: the price excludes GST. Taxable value = price less discount; GST = rate x taxable
  value, charged at checkout (`booking.tax_cents`), and the invoice shows exactly that tax.
- **Packages**: the price includes GST, so the tax is taken out of it.
- **Rate**: `service.gst_rate_pct`, else `spa.tax_percent` (default 18). A spa with no valid GSTIN
  charges no GST at checkout and issues a bill of supply.
- **SAC**: `service.sac_code`, else 999722 (beauty and physical well-being services).
- **CGST/SGST or IGST**: a treatment is supplied where it is performed (IGST Act s.12(4)), so a guest's
  invoice is always CGST + SGST in the spa's state; any odd paisa goes to CGST. The commission invoice
  is IGST when Nirvana's state (from its GSTIN) differs from the spa's, else CGST + SGST.
- **Credit notes** reverse the refund in the invoice's own split of taxable value and tax, and never
  credit more than the invoice.
- **Commission**: `spa.commission_pct` of the month's online collections (bookings and packages paid
  through Nirvana), less refunds of them, counted by India date; plus 18% GST. SAC 998599.

## Configuration

| Setting | Env var | Default |
|---|---|---|
| `gst.nirvana.gstin` | `NIRVANA_GSTIN` | none: commission invoices are not issued until set |
| `gst.nirvana.legal-name` | `NIRVANA_LEGAL_NAME` | Nirvana Wellness Private Limited |
| `gst.nirvana.address` | `NIRVANA_REGISTERED_ADDRESS` | none |
| `gst.default-sac` | `GST_DEFAULT_SAC` | 999722 |
| `gst.commission.sac` / `rate-pct` | `GST_COMMISSION_SAC` / `GST_COMMISSION_RATE_PCT` | 998599 / 18 |
| `gst.sweep.lookback-days` | `GST_SWEEP_LOOKBACK_DAYS` | 45 (0 turns the catch-up job off) |

Spas enter their GSTIN and legal name in KYC; an invalid GSTIN (bad format or check character) is
refused.

## Where people get them

- **Guest**: My bookings, "View GST invoice" (and a button per credit note). Opens a printable page;
  the browser's print dialog saves it as PDF. The invoice is also emailed through the existing invoice
  email when issued, and the existing PDF download now carries the GST details.
- **Owner**: Owner dashboard, Payments, "GST invoices": the month's invoices and credit notes with
  totals, and Nirvana's commission invoice for a past month.
- A background job (every 10 minutes) issues anything missed: invoices, credit notes, and last month's
  commission invoices.

## API

- `GET /api/bookings/{id}/gst-documents`: the guest's invoice and credit notes (issues what is due).
- `GET /api/me/spa-packages/{subscriptionId}/gst-documents`: the same for a spa package purchase.
- `GET /api/gst-documents/{id}/html`: the printable document (guest, spa desk, owner or admin;
  commission invoices for the owner and admins only).
- `GET /api/owner/spas/{spa}/gst-documents?from=&to=`: the spa's documents by issue date.
- `POST /api/owner/spas/{spa}/commission-invoices/{yyyy-mm}`: Nirvana's commission invoice for the
  month (204 when there was no commission).
