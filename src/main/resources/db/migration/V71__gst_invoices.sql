-- GST documents. Under the marketplace model the spa is the supplier to the guest and issues the
-- guest's tax invoice (or a bill of supply when it has no GSTIN); Nirvana separately invoices the
-- spa for its commission. Each document is a frozen snapshot: names, addresses, GSTINs and amounts
-- are copied in at issue so later edits to a spa or booking never change an issued document.

-- The name registered against the spa's GSTIN, when it differs from the trading name.
ALTER TABLE spa ADD COLUMN legal_name VARCHAR(255);

-- Per-service overrides. NULL means the defaults: SAC 999722 and the spa's tax_percent.
ALTER TABLE service
    ADD COLUMN sac_code VARCHAR(8),
    ADD COLUMN gst_rate_pct DECIMAL(5,2);

-- One counter per issuer, financial year and series (I invoice, B bill of supply, C credit note).
-- The counter is bumped (INSERT ... ON DUPLICATE KEY UPDATE, which locks the row) in the same
-- transaction that inserts the document, so a rolled-back issue gives its number back and the series
-- stays gap-free.
CREATE TABLE gst_document_sequence (
    issuer VARCHAR(32) NOT NULL,
    financial_year CHAR(7) NOT NULL,
    series CHAR(1) NOT NULL,
    last_number BIGINT NOT NULL,
    PRIMARY KEY (issuer, financial_year, series)
);

CREATE TABLE gst_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- TAX_INVOICE, BILL_OF_SUPPLY, CREDIT_NOTE or COMMISSION_INVOICE.
    doc_type VARCHAR(24) NOT NULL,
    -- SPA:<id> for the spa's own documents, NIRVANA for Nirvana's.
    issuer VARCHAR(32) NOT NULL,
    document_number VARCHAR(16) NOT NULL,
    financial_year CHAR(7) NOT NULL,
    series CHAR(1) NOT NULL,
    sequence_number BIGINT NOT NULL,
    issue_date DATE NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    -- What the document is for, so it is issued once: BOOKING:<id>, PACKAGE:<id>,
    -- REFUND:BOOKING:<payment>:<cumulative>, REFUND:PACKAGE:<subscription>:<cumulative>,
    -- REFUND:DESK:<receipt>, COMMISSION:<spa>:<yyyy-mm>.
    source_key VARCHAR(80) NOT NULL,
    spa_id BIGINT NOT NULL,
    user_id BIGINT,
    booking_id BIGINT,
    subscription_id BIGINT,
    original_document_id BIGINT,
    period_start DATE,
    period_end DATE,
    supplier_name VARCHAR(255) NOT NULL,
    supplier_trade_name VARCHAR(255),
    supplier_address VARCHAR(600),
    supplier_gstin VARCHAR(15),
    supplier_state VARCHAR(100),
    supplier_state_code CHAR(2),
    recipient_name VARCHAR(255),
    recipient_address VARCHAR(600),
    recipient_gstin VARCHAR(15),
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(40),
    place_of_supply VARCHAR(100),
    place_of_supply_code CHAR(2),
    description VARCHAR(500) NOT NULL,
    sac_code VARCHAR(8),
    quantity INT NOT NULL DEFAULT 1,
    currency VARCHAR(8) NOT NULL,
    -- Price before discount, the discount, and the taxable value (gross less discount, net of GST).
    gross_cents BIGINT NOT NULL,
    discount_cents BIGINT NOT NULL DEFAULT 0,
    taxable_cents BIGINT NOT NULL,
    -- The rate in hundredths of a percent: 1800 is 18%. 0 on a bill of supply.
    rate_bps INT NOT NULL,
    cgst_cents BIGINT NOT NULL DEFAULT 0,
    sgst_cents BIGINT NOT NULL DEFAULT 0,
    igst_cents BIGINT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL,
    -- For a commission invoice: the online collections, net of refunds, the commission is on.
    commission_base_cents BIGINT,
    commission_pct DECIMAL(5,2),
    notes VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_gst_document_number (issuer, document_number),
    UNIQUE KEY uq_gst_document_source (source_key),
    INDEX ix_gst_document_spa (spa_id, issued_at),
    INDEX ix_gst_document_booking (booking_id),
    INDEX ix_gst_document_subscription (subscription_id),
    INDEX ix_gst_document_user (user_id, issued_at),
    FOREIGN KEY (spa_id) REFERENCES spa(id),
    FOREIGN KEY (booking_id) REFERENCES booking(id),
    FOREIGN KEY (subscription_id) REFERENCES user_spa_package_subscription(id),
    FOREIGN KEY (original_document_id) REFERENCES gst_document(id)
);
