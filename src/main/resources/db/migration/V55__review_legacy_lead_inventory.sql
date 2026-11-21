-- Only spas created with the lead importer have its schedule-rule origin.
-- Keep appointments, rooms and schedules available for operational review.
UPDATE spa s
JOIN (SELECT spa_id, MIN(id) lead_id FROM spa_lead WHERE spa_id IS NOT NULL GROUP BY spa_id) l ON l.spa_id=s.id
SET s.meta=JSON_SET(COALESCE(s.meta, JSON_OBJECT()), '$.onboardedFromLead', l.lead_id),
    s.is_active=0, s.is_verified=0, s.kyc_status='PENDING', s.kyc_approved_at=NULL
WHERE s.is_active=1 AND EXISTS (
    SELECT 1 FROM schedule_rule r WHERE r.spa_id=s.id AND r.note='From Google Maps at onboarding'
);
