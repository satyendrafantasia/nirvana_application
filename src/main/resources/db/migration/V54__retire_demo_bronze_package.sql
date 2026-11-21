-- The Global Bronze package (5 sessions for Rs 200) came from the V18 demo seed and was being
-- offered at every spa. Stop selling it. Anyone who already holds one keeps their sessions, and
-- its spa links stay so those sessions can still be used.
UPDATE packages SET is_active = 0, updated_at = NOW(6)
WHERE package_type = 'GLOBAL_BRONZE' AND price_cents = 20000;
