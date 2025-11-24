-- Add new cents columns if they don't already exist
ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS amount_cents   INT NULL,
    ADD COLUMN IF NOT EXISTS tax_cents      INT NULL,
    ADD COLUMN IF NOT EXISTS discount_cents INT NULL,
    ADD COLUMN IF NOT EXISTS total_cents    INT NULL;

-- If you *haven't* already populated them manually, this will fill them.
-- If you already did, running it again is harmless.
UPDATE invoice
SET
    amount_cents   = COALESCE(amount_cents,
                              ROUND(COALESCE(amount_subtotal, 0)  * 100)),
    tax_cents      = COALESCE(tax_cents,
                              ROUND(COALESCE(tax_amount, 0)       * 100)),
    discount_cents = COALESCE(discount_cents,
                              ROUND(COALESCE(discount_amount, 0)  * 100)),
    total_cents    = COALESCE(total_cents,
                              ROUND(COALESCE(amount_total, 0)     * 100));

-- Make them NOT NULL now that they are populated
ALTER TABLE invoice
    MODIFY amount_cents   INT NOT NULL,
    MODIFY tax_cents      INT NOT NULL,
    MODIFY discount_cents INT NOT NULL,
    MODIFY total_cents    INT NOT NULL;

-- Drop old decimal columns if they still exist
ALTER TABLE invoice
    DROP COLUMN IF EXISTS amount_subtotal,
    DROP COLUMN IF EXISTS tax_amount,
    DROP COLUMN IF EXISTS discount_amount,
    DROP COLUMN IF EXISTS amount_total;
