-- Spas onboarded from leads take bookings like any other spa instead of enquiries only (V69 made them
-- ENQUIRY). The spa still gets the SMS/WhatsApp link to confirm or decline each booking, and a decline
-- refunds the guest in full. A spa whose booking mode was set by its owner or staff (it has a row in
-- booking_policy_history) keeps that choice.
UPDATE spa s
SET s.booking_mode = 'INSTANT'
WHERE s.booking_mode = 'ENQUIRY'
  AND JSON_EXTRACT(s.meta, '$.onboardedFromLead') IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM booking_policy_history h WHERE h.spa_id = s.id);
