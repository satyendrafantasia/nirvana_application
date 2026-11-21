-- Google Maps search exports carry each spa's Place ID, which links a lead to its Google listing
-- (and so to an owner's listing claim). Older exports only carry the Maps cid.
ALTER TABLE spa_lead
  ADD COLUMN google_place_id varchar(255) DEFAULT NULL AFTER google_cid,
  ADD UNIQUE KEY uk_spa_lead_place (google_place_id);
