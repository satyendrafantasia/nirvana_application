-- Add-ons and upgrades a guest can choose with a treatment: "+30 min", hot stones, aromatherapy oil.
-- Prices are in the smallest currency unit, as on service. extra_minutes lengthens the appointment.
CREATE TABLE spa_addon (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    spa_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    price_cents INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    extra_minutes INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_spa_addon_spa FOREIGN KEY (spa_id) REFERENCES spa(id),
    CONSTRAINT chk_spa_addon_values CHECK (price_cents >= 0 AND extra_minutes BETWEEN 0 AND 240),
    INDEX ix_spa_addon_spa (spa_id, is_active, sort_order)
) ENGINE=InnoDB;

-- An add-on with no rows here applies to every treatment at the spa.
CREATE TABLE spa_addon_service (
    addon_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (addon_id, service_id),
    CONSTRAINT fk_spa_addon_service_addon FOREIGN KEY (addon_id) REFERENCES spa_addon(id) ON DELETE CASCADE,
    CONSTRAINT fk_spa_addon_service_service FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE,
    INDEX ix_spa_addon_service_service (service_id)
) ENGINE=InnoDB;

-- What the guest chose, as it was when they booked: later edits to the add-on don't change it.
-- booking.price_cents already includes amount_cents; these rows say how it splits.
CREATE TABLE booking_addon (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    addon_id BIGINT NULL,
    name VARCHAR(120) NOT NULL,
    unit_price_cents INT NOT NULL,
    quantity INT NOT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    extra_minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_booking_addon_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_addon_addon FOREIGN KEY (addon_id) REFERENCES spa_addon(id) ON DELETE SET NULL,
    CONSTRAINT chk_booking_addon_values CHECK (unit_price_cents >= 0 AND quantity > 0
        AND amount_cents = unit_price_cents * quantity AND extra_minutes BETWEEN 0 AND 240),
    INDEX ix_booking_addon_booking (booking_id)
) ENGINE=InnoDB;

-- A reservation keeps the therapist and room for the treatment plus its add-on minutes.
ALTER TABLE booking_hold ADD COLUMN extra_minutes INT NOT NULL DEFAULT 0;
