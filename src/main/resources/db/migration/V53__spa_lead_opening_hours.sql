-- Opening hours as Google Maps listed them when the lead was imported, e.g. {"monday":"10 AM–10 PM"}.
-- Used once, to set the spa's hours when the lead is onboarded; the spa's own schedule wins after that.
ALTER TABLE spa_lead ADD COLUMN opening_hours_json TEXT NULL;

-- Who confirmed the spa agreed to be listed, and when, for leads an admin onboards directly.
ALTER TABLE spa_lead ADD COLUMN permission_confirmed_by bigint NULL;
ALTER TABLE spa_lead ADD COLUMN permission_confirmed_at datetime(6) NULL;
