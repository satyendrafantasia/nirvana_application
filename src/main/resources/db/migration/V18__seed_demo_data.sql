-- Demo seed data to make API testing easier across the schema
SET @now = NOW(6);

-- Core roles
INSERT IGNORE INTO role (id, role_type) VALUES
  (1, 'ADMIN'),
  (2, 'CUSTOMER'),
  (3, 'Spa_MANAGER');

-- Application users
INSERT INTO app_user (
  id, created_at, updated_at, active, deleted_at, display_name, email, email_verified,
  is_active, last_login_at, last_name, locale, loyalty_points, marketing_opt_in, meta,
  mfa_enabled, name, password, password_hash, password_salt, phone, phone_verified,
  preferred_contact_method, profile_image_url, referral_code, referred_by, timezone,
  username, version, role_id, privacy_consent_version, privacy_consented_at, consent_source,
  data_erasure_requested_at, data_erased_at
) VALUES
  (1, @now, @now, b'1', NULL, 'Super Admin', 'admin@nirvana.test', b'1', b'1', @now,
   'Admin', 'en', 0, b'0', NULL, b'0', 'Admin User', '{noop}password', NULL, NULL,
   '+10000000001', b'1', 'EMAIL', NULL, 'REF-ADMIN', NULL, 'Asia/Kolkata', 'admin', 1, 1,
   'v1', @now, 'WEB', NULL, NULL),
  (2, @now, @now, b'1', NULL, 'Spa Manager', 'manager@nirvana.test', b'1', b'1', @now,
   'Manager', 'en', 10, b'1', NULL, b'0', 'Maya Manager', '{noop}password', NULL, NULL,
   '+10000000002', b'1', 'EMAIL', NULL, 'REF-MANAGER', 'REF-ADMIN', 'Asia/Kolkata', 'manager', 1, 3,
   'v1', @now, 'WEB', NULL, NULL),
  (3, @now, @now, b'1', NULL, 'Provider Lead', 'provider@nirvana.test', b'1', b'1', @now,
   'Provider', 'en', 5, b'1', NULL, b'0', 'Priya Provider', '{noop}password', NULL, NULL,
   '+10000000003', b'1', 'PHONE', NULL, 'REF-PROVIDER', 'REF-MANAGER', 'Asia/Kolkata', 'provider', 1, 3,
   'v1', @now, 'WEB', NULL, NULL),
  (4, @now, @now, b'1', NULL, 'Test Customer', 'customer@nirvana.test', b'1', b'1', @now,
   'Customer', 'en', 120, b'1', NULL, b'0', 'Chirag Customer', '{noop}password', NULL, NULL,
   '+10000000004', b'1', 'EMAIL', NULL, 'REF-CUST', 'REF-ADMIN', 'Asia/Kolkata', 'customer', 1, 2,
   'v1', @now, 'WEB', NULL, NULL),
  (5, @now, @now, b'1', NULL, 'Therapist User', 'therapist@nirvana.test', b'1', b'1', @now,
   'Therapist', 'en', 30, b'0', NULL, b'0', 'Tanvi Therapist', '{noop}password', NULL, NULL,
   '+10000000005', b'1', 'EMAIL', NULL, 'REF-THER', 'REF-MANAGER', 'Asia/Kolkata', 'therapist', 1, 2,
   'v1', @now, 'WEB', NULL, NULL),
  (6, @now, @now, b'1', NULL, 'Corporate User', 'employee@corp.test', b'1', b'1', @now,
   'Employee', 'en', 45, b'1', NULL, b'0', 'Esha Employee', '{noop}password', NULL, NULL,
   '+10000000006', b'1', 'EMAIL', NULL, 'REF-CORP', 'REF-ADMIN', 'Asia/Kolkata', 'corporate', 1, 2,
   'v1', @now, 'WEB', NULL, NULL)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- User roles mapping for Spring Security compatibility
INSERT IGNORE INTO user_roles (user_id, role) VALUES
  (1, 'ROLE_ADMIN'),
  (2, 'ROLE_Spa_MANAGER'),
  (3, 'ROLE_Spa_MANAGER'),
  (4, 'ROLE_CUSTOMER'),
  (5, 'ROLE_CUSTOMER'),
  (6, 'ROLE_CUSTOMER');

-- Spa manager profile
INSERT IGNORE INTO spa_manager (id, user_id) VALUES
  (1, 2);

-- Provider roster
INSERT IGNORE INTO provider_user (
  id, created_at, updated_at, bank_account_masked, certifications, display_name, email, employee_code,
  hire_date, id_doc_url, is_active, languages, meta, name, payout_method, phone, rating_avg,
  rating_count, termination_date, title, version, work_hours_json
) VALUES
  (1, @now, @now, 'XXXX1234', JSON_ARRAY('Swedish Massage'), 'Lead Therapist', 'therapist.provider@nirvana.test', 'PR-001',
   DATE_SUB(@now, INTERVAL 400 DAY), NULL, b'1', JSON_ARRAY('English', 'Hindi'), NULL, 'Priya Provider', 'BANK', '+910000000003', 4.8, 56, NULL, 'Therapist', 1, NULL),
  (2, @now, @now, 'XXXX5678', NULL, 'Front Desk', 'frontdesk@nirvana.test', 'FD-001',
   DATE_SUB(@now, INTERVAL 200 DAY), NULL, b'1', JSON_ARRAY('English'), NULL, 'Front Desk Lead', 'BANK', '+910000000007', 4.6, 18, NULL, 'Front Desk', 1, NULL);

-- Spa catalog
INSERT IGNORE INTO spa (
  id, created_at, updated_at, address_line1, address_line2, address_type, city, country,
  country_code, formatted_address, geo_source, google_place_id, landmark, latitude, locality,
  longitude, meta, postal_code, state, timezone, amenities, business_reg_number, commission_pct,
  default_currency, description, email, established_at, facebook_url, gstin, images, instagram_url,
  is_active, is_featured, is_verified, kyc_approved_at, kyc_rejected_at, kyc_rejected_reason,
  kyc_requested_at, kyc_status, max_advance_booking_days, max_concurrent_services,
  min_notice_minutes, name, owner_name, phone, rating_avg, rating_count, tags, tax_percent,
  total_bookings, version, website_url, spa_manager_id, allow_therapist_selection, allow_therapist_type_selection
) VALUES
  (1, @now, @now, '221B Baker Street', 'Near Regent Park', 'COMMERCIAL', 'London', 'United Kingdom', 'GB',
   '221B Baker Street, London', 'MAPS', 'place_123', 'Regent Park Gate', 51.523767, 'Marylebone', -0.158555,
   JSON_OBJECT('parking', 'valet'), 'NW1', 'London', 'Europe/London', JSON_ARRAY('Sauna', 'Steam'), 'BRN-001', 20,
   'GBP', 'Flagship wellness spa for city professionals', 'hello@nirvana.test', DATE_SUB(@now, INTERVAL 5 YEAR),
   'https://facebook.com/nirvana', 'GSTIN123', JSON_ARRAY('https://cdn.nirvana/hero.jpg'),
   'https://instagram.com/nirvana', b'1', b'1', b'1', DATE_SUB(@now, INTERVAL 2 YEAR), NULL, NULL,
   DATE_SUB(@now, INTERVAL 2 YEAR), 'VERIFIED', 60, 4, 120, 'Nirvana Marylebone', 'Maya Manager', '+44-20-7946-0958', 4.7,
   1240, JSON_ARRAY('city-centre', 'luxury'), 18, 35210, 1, 'https://nirvana.test', 1, b'1', b'1');

-- Generate 100 additional spa locations with consistent dependent records for broader API testing
WITH RECURSIVE spa_seed AS (
  SELECT 2 AS id
  UNION ALL
  SELECT id + 1 FROM spa_seed WHERE id < 101
)
INSERT IGNORE INTO spa (
  id, created_at, updated_at, address_line1, address_line2, address_type, city, country,
  country_code, formatted_address, geo_source, google_place_id, landmark, latitude, locality,
  longitude, meta, postal_code, state, timezone, amenities, business_reg_number, commission_pct,
  default_currency, description, email, established_at, facebook_url, gstin, images, instagram_url,
  is_active, is_featured, is_verified, kyc_approved_at, kyc_rejected_at, kyc_rejected_reason,
  kyc_requested_at, kyc_status, max_advance_booking_days, max_concurrent_services,
  min_notice_minutes, name, owner_name, phone, rating_avg, rating_count, tags, tax_percent,
  total_bookings, version, website_url, spa_manager_id, allow_therapist_selection, allow_therapist_type_selection
)
SELECT
  id,
  @now,
  @now,
  CONCAT(id, ' Wellness Street'),
  CONCAT('Suite ', LPAD(id, 3, '0')),
  'COMMERCIAL',
  CONCAT('City ', MOD(id, 20) + 1),
  'India',
  'IN',
  CONCAT(id, ' Wellness Street, City ', MOD(id, 20) + 1),
  'MAPS',
  CONCAT('place_', id),
  CONCAT('Landmark ', id),
  12.900000 + (id * 0.01),
  CONCAT('Locality ', MOD(id, 15) + 1),
  77.500000 + (id * 0.01),
  JSON_OBJECT('parking', IF(MOD(id, 2) = 0, 'valet', 'street')),
  CONCAT('560', LPAD(id, 3, '0')),
  'Karnataka',
  'Asia/Kolkata',
  JSON_ARRAY('Sauna', 'Steam', 'Pool'),
  CONCAT('BRN-', LPAD(id, 4, '0')),
  18,
  'INR',
  CONCAT('Demo spa location ', id, ' for API load testing'),
  CONCAT('location', id, '@nirvana.test'),
  DATE_SUB(@now, INTERVAL (id * 3) DAY),
  NULL,
  CONCAT('GSTIN', LPAD(id, 4, '0')),
  JSON_ARRAY(CONCAT('https://cdn.nirvana/spa', id, '/hero.jpg')),
  NULL,
  b'1',
  IF(MOD(id, 10) = 0, b'1', b'0'),
  b'1',
  DATE_SUB(@now, INTERVAL (id * 2) DAY),
  NULL,
  NULL,
  DATE_SUB(@now, INTERVAL (id * 2) DAY),
  'VERIFIED',
  45,
  3,
  90,
  CONCAT('Nirvana Location ', id),
  CONCAT('Owner ', id),
  CONCAT('+91-90000', LPAD(id, 4, '0')),
  4.0 + (MOD(id, 10) * 0.02),
  100 + id,
  JSON_ARRAY('franchise', 'demo'),
  18,
  2000 + id,
  1,
  CONCAT('https://nirvana.test/spa/', id),
  1,
  b'1',
  b'1'
FROM spa_seed;

-- Spa rooms
INSERT IGNORE INTO spa_room (id, created_at, updated_at, is_active, capacity, code, meta, name, version, spa_id) VALUES
  (1, @now, @now, b'1', 2, 'RM-DELUXE', NULL, 'Deluxe Room', 1, 1),
  (2, @now, @now, b'1', 1, 'RM-COUPLE', NULL, 'Couple Suite', 1, 1);

WITH RECURSIVE spa_seed AS (
  SELECT 2 AS id
  UNION ALL
  SELECT id + 1 FROM spa_seed WHERE id < 101
)
INSERT IGNORE INTO spa_room (id, created_at, updated_at, is_active, capacity, code, meta, name, version, spa_id)
SELECT
  1000 + id,
  @now,
  @now,
  b'1',
  IF(MOD(id, 3) = 0, 3, 2),
  CONCAT('RM-', LPAD(id, 3, '0')),
  NULL,
  CONCAT('Therapy Room ', id),
  1,
  id
FROM spa_seed;

-- Operating hours
INSERT IGNORE INTO schedule_rule (id, applies_from, applies_to, close_local, is_holiday, meta, note, open_local, version, weekday, spa_id) VALUES
  (1, CURDATE(), NULL, '21:00:00', b'0', NULL, 'Standard hours', '09:00:00', 1, 1, 1),
  (2, CURDATE(), NULL, '21:00:00', b'0', NULL, 'Standard hours', '09:00:00', 1, 5, 1);

WITH RECURSIVE spa_seed AS (
  SELECT 2 AS id
  UNION ALL
  SELECT id + 1 FROM spa_seed WHERE id < 101
)
INSERT IGNORE INTO schedule_rule (id, applies_from, applies_to, close_local, is_holiday, meta, note, open_local, version, weekday, spa_id)
SELECT
  2000 + id,
  CURDATE(),
  NULL,
  '21:00:00',
  b'0',
  NULL,
  CONCAT('Standard hours for spa ', id),
  '08:00:00',
  1,
  MOD(id, 7) + 1,
  id
FROM spa_seed;

-- Services
INSERT IGNORE INTO service (
  id, created_at, updated_at, base_price_cents, buffer_min, cancellation_policy_json, category, currency,
  description, duration_min, duration_minutes, equipment_required, gender_allowed, images, is_active,
  is_visible_on_marketplace, max_persons, meta, min_persons, name, price_breakdown, price_cents,
  service_code, sub_category, therapist_gender_preference, version, spa_id
) VALUES
  (1, @now, @now, 12000, 15, JSON_OBJECT('window_hours', 4), 'Massage', 'GBP', '60 minute deep tissue massage',
   60, 60, NULL, 'ANY', JSON_ARRAY('https://cdn.nirvana/massage.jpg'), b'1', b'1', 1, NULL, 1, 'Deep Tissue Massage',
   NULL, 12000, 'DTM-60', 'Relaxation', NULL, 1, 1),
  (2, @now, @now, 8000, 10, JSON_OBJECT('window_hours', 6), 'Facial', 'GBP', 'Refreshing facial treatment',
   45, 45, NULL, 'ANY', JSON_ARRAY('https://cdn.nirvana/facial.jpg'), b'1', b'1', 1, NULL, 1, 'Hydrating Facial',
   NULL, 8000, 'FAC-45', 'Skincare', NULL, 1, 1);

WITH RECURSIVE spa_seed AS (
  SELECT 2 AS id
  UNION ALL
  SELECT id + 1 FROM spa_seed WHERE id < 101
)
INSERT IGNORE INTO service (
  id, created_at, updated_at, base_price_cents, buffer_min, cancellation_policy_json, category, currency,
  description, duration_min, duration_minutes, equipment_required, gender_allowed, images, is_active,
  is_visible_on_marketplace, max_persons, meta, min_persons, name, price_breakdown, price_cents,
  service_code, sub_category, therapist_gender_preference, version, spa_id
)
SELECT
  3000 + id,
  @now,
  @now,
  5000 + (id * 10),
  10,
  JSON_OBJECT('window_hours', 24),
  'Massage',
  'INR',
  CONCAT('Signature relaxation massage at location ', id),
  50,
  50,
  NULL,
  'ANY',
  JSON_ARRAY(CONCAT('https://cdn.nirvana/spa', id, '/service.jpg')),
  b'1',
  b'1',
  1,
  NULL,
  1,
  CONCAT('Relaxation Massage ', id),
  NULL,
  5000 + (id * 10),
  CONCAT('SRV-', LPAD(id, 4, '0')),
  'Relaxation',
  NULL,
  1,
  id
FROM spa_seed;

-- Therapist roster
INSERT IGNORE INTO therapist (
  id, created_at, updated_at, bio, certifications, country_of_origin, currency, display_name, email, ethnicity,
  experience_years, gender, hourly_rate_cents, images, is_active, is_available, languages, last_seen_at, meta,
  name, phone, profile_image_url, rating_avg, rating_count, reviews, type, version, service_id, spa_id
) VALUES
  (1, @now, @now, 'Expert in deep tissue and sports recovery', JSON_ARRAY('Swedish Massage', 'Sports Therapy'), 'India', 'GBP',
   'Tanvi T', 'therapist@nirvana.test', 'Asian', 8, 'FEMALE', 4500, JSON_ARRAY('https://cdn.nirvana/therapists/tanvi.jpg'),
   b'1', b'1', JSON_ARRAY('English', 'Hindi'), @now, NULL, 'Tanvi Therapist', '+44-20-0000-0005', NULL, 4.9, 310, NULL,
   'LEAD', 1, 1, 1);

INSERT IGNORE INTO therapist_service (therapist_id, service_id) VALUES
  (1, 1),
  (1, 2);

-- Slots for availability
INSERT IGNORE INTO slot (
  id, created_at, updated_at, booked_units, capacity_unit, end_ts, hold_expires_ts, hold_token, is_blocked, meta,
  room_number, start_ts, status, version, assigned_provider_user_id, service_id, spa_id, room_id
) VALUES
  (1, @now, @now, 0, 1, DATE_ADD(@now, INTERVAL 1 HOUR), NULL, NULL, b'0', NULL, 'Deluxe Room', @now, 'OPEN', 1, 1, 1, 1, 1),
  (2, @now, @now, 0, 1, DATE_ADD(@now, INTERVAL 2 HOUR), NULL, NULL, b'0', NULL, 'Couple Suite', DATE_ADD(@now, INTERVAL 1 HOUR), 'OPEN', 1, 2, 2, 1, 2);

-- Membership catalog
INSERT IGNORE INTO membership_plan (id, created_at, updated_at, is_active, benefits, currency, description, duration_days, meta, name, price_cents, version, spa_id) VALUES
  (1, @now, @now, b'1', JSON_OBJECT('free_sessions', 2, 'priority_support', true), 'GBP', 'Monthly loyalty membership', 30, NULL, 'Nirvana Monthly', 5000, 1, 1);

INSERT IGNORE INTO user_membership (id, created_at, updated_at, end_at, meta, start_at, status, version, plan_id, spa_id, user_id) VALUES
  (1, @now, @now, DATE_ADD(@now, INTERVAL 30 DAY), NULL, @now, 'ACTIVE', 1, 1, 1, 4);

-- Packages and subscriptions
INSERT IGNORE INTO packages (id, created_at, updated_at, package_type, price_cents, session_count, description, validity_days, is_active) VALUES
  (1, @now, @now, 'GLOBAL_BRONZE', 20000, 5, 'Five session starter pack', 180, 1);

INSERT IGNORE INTO service_package_spa (package_id, spa_id) VALUES (1, 1);

INSERT IGNORE INTO user_package_subscription (
  id, created_at, updated_at, user_id, package_id, package_type, purchase_date, expiry_date, remaining_sessions,
  status, payment_status, payment_reference, activated_at, last_used_at, version
) VALUES
  (1, @now, @now, 4, 1, 'GLOBAL_BRONZE', @now, DATE_ADD(@now, INTERVAL 6 MONTH), 5, 'ACTIVE', 'PAID', 'PAY-GPB-001', @now, NULL, 1);

INSERT IGNORE INTO user_package_usage_log (id, created_at, updated_at, subscription_id, user_id, booking_id, spa_id, session_number, used_at) VALUES
  (1, @now, @now, 1, 4, 1, 1, 1, @now);

INSERT IGNORE INTO spa_packages (id, created_at, updated_at, spa_id, level, price, free_sessions_count, status, version) VALUES
  (1, @now, @now, 1, 'GOLD', 300.00, 2, 'ACTIVE', 1);

INSERT IGNORE INTO user_spa_package_subscription (id, created_at, updated_at, user_id, spa_id, spa_package_id, level, price_paid, total_sessions, remaining_sessions, status, purchase_date, expiry_date, payment_status, payment_reference_id, version) VALUES
  (1, @now, @now, 4, 1, 1, 'GOLD', 300.00, 4, 3, 'ACTIVE', @now, DATE_ADD(@now, INTERVAL 1 YEAR), 'PAID', 'PAY-SPA-001', 1);

INSERT IGNORE INTO user_spa_package_usage_log (id, created_at, updated_at, user_spa_package_subscription_id, user_id, spa_id, booking_id, usage_date, session_number, notes) VALUES
  (1, @now, @now, 1, 4, 1, 1, @now, 1, 'Used during initial visit');

-- Coupons and vouchers
INSERT IGNORE INTO coupon (id, created_at, updated_at, is_active, applies_to_services, code, discount_type, discount_value, max_discount_cents, max_uses_global, max_uses_per_user, meta, min_order_cents, valid_from, valid_to, version, spa_id) VALUES
  (1, @now, @now, b'1', JSON_ARRAY(1,2), 'WELCOME10', 'PERCENTAGE', 10, 2000, 500, 2, NULL, 5000, DATE_SUB(@now, INTERVAL 7 DAY), DATE_ADD(@now, INTERVAL 30 DAY), 1, 1);

INSERT IGNORE INTO voucher (id, created_at, updated_at, code, user_id, booking_id, amount_cents, currency, note, issued_by, expires_at, redeemed_at, meta) VALUES
  (1, @now, @now, 'VCHR-001', 4, NULL, 1500, 'GBP', 'Goodwill voucher', 'Admin', DATE_ADD(@now, INTERVAL 90 DAY), NULL, NULL);

-- Spa onboarding drafts
INSERT IGNORE INTO onboarding_draft (id, owner_user_id, spa_id, step_key, payload, resume_token, expires_at, last_client_event_at, client_request_id, created_at, updated_at, version) VALUES
  (1, 2, 1, 'services', '{"step":"services","completed":true}', 'TOKEN-ABC', DATE_ADD(@now, INTERVAL 7 DAY), @now, 'REQ-123', @now, @now, 1);

-- Booking and related events
INSERT IGNORE INTO booking (
  id, created_at, updated_at, arrived_at, booking_reference, cancellation_reason_code, cancellation_reason_text,
  cancelled_at, cancelled_by, channel, completed_at, coupon_code, currency, customer_notes, deposit_cents,
  discount_cents, end_ts, external_booking_id, guest_count, invoice_url, ip_address, is_test_booking, items,
  last_status_changed_at, meta, no_show_marked_at, policy_snapshot, price_cents, provider_notes, rating_given,
  refund_status, remainder_cents, reschedule_count, scheduled_by, start_ts, status, tax_breakdown, tax_cents,
  tip_cents, version, provider_assigned_id, therapist_id, therapist_type, service_id, slot_id, spa_id, user_id,
  coupon_id, user_membership_id, package_subscription_id, payment_mode, payment_type, payment_source_type,
  corporate_employee_coupon_id
) VALUES
  (1, @now, @now, NULL, 'BOOK-0001', NULL, NULL, NULL, NULL, 'WEB', NULL, 'WELCOME10', 'GBP', 'Please assign a quiet room',
   2000, 1200, DATE_ADD(@now, INTERVAL 1 HOUR), NULL, 1, NULL, '127.0.0.1', b'0', JSON_OBJECT('services', JSON_ARRAY(1)),
   @now, NULL, NULL, JSON_OBJECT('cancellation', '24hrs'), 12000, 'First visit notes', b'0', 'NONE', 8000, 0, 'USER', @now,
   'CONFIRMED', JSON_OBJECT('tax_rate', 0.18), 2160, 500, 1, 1, 1, 'FEMALE', 1, 1, 1, 4, 1, 1, 1, 'ONLINE', 'STANDARD', 'NORMAL', NULL);

INSERT IGNORE INTO booking_event (id, actor_id, actor_type, created_at, event_payload, new_status, prev_status, reason, version, booking_id) VALUES
  (1, 4, 'CUSTOMER', @now, JSON_OBJECT('note','Initial confirmation'), 'CONFIRMED', NULL, 'Booked via web', 1, 1);

INSERT IGNORE INTO booking_hold (id, user_id, spa_id, slot_id, services_json, hold_token, expires_at, hold_units, converted_to_booking, meta, created_at, updated_at, version) VALUES
  (1, 4, 1, 2, JSON_ARRAY(2), 'HOLD-ABC', DATE_ADD(@now, INTERVAL 30 MINUTE), 1, FALSE, NULL, @now, @now, 1);

INSERT IGNORE INTO waitlist_entry (id, user_id, spa_id, service_id, slot_id, guests, contact_email, contact_phone, active, notified, notified_at, meta, created_at, updated_at, version) VALUES
  (1, 4, 1, 1, NULL, 1, 'customer@nirvana.test', '+10000000004', TRUE, FALSE, NULL, NULL, @now, @now, 1);

-- Operational controls
INSERT IGNORE INTO closure (
  id, end_ts, is_recurring, meta, reason, rrule, scope, start_ts, type, version, spa_id
) VALUES
  (1, DATE_ADD(@now, INTERVAL 1 DAY), b'0', NULL, 'Maintenance window', NULL, 'FULL', @now, 'MAINTENANCE', 1, 1);

INSERT IGNORE INTO blackout_window (id, spa_id, provider_id, start_ts, end_ts, reason, created_by, created_at, updated_at) VALUES
  (1, 1, 1, DATE_ADD(@now, INTERVAL 3 DAY), DATE_ADD(DATE_ADD(@now, INTERVAL 3 DAY), INTERVAL 2 HOUR), 'Staff training', 'Maya Manager', @now, @now);

INSERT IGNORE INTO provider_leave (id, created_at, updated_at, end_ts, reason, start_ts, status, version, approved_by_user_id, provider_user_id, spa_id) VALUES
  (1, @now, @now, DATE_ADD(@now, INTERVAL 2 DAY), 'Medical leave', DATE_ADD(@now, INTERVAL 1 DAY), 'APPROVED', 1, 2, 1, 1);

INSERT IGNORE INTO admin_action_log (id, created_at, updated_at, action_type, after_state, before_state, entity_id, entity_type, reason, version, actor_user_id) VALUES
  (1, @now, @now, 'CREATE', JSON_OBJECT('name','Nirvana Marylebone'), NULL, 1, 'spa', 'Initial setup', 1, 1);

-- Billing artifacts
INSERT IGNORE INTO invoice (id, created_at, updated_at, currency, discount_cents, due_at, invoice_number, invoice_pdf_url, issued_at, meta, status, tax_cents, version, booking_id, spa_id, user_id, amount_cents, total_cents) VALUES
  (1, @now, @now, 'GBP', 1200, DATE_ADD(@now, INTERVAL 7 DAY), 'INV-0001', 'https://cdn.nirvana/invoices/INV-0001.pdf', @now, NULL, 'ISSUED', 2160, 1, 1, 1, 4, 12000, 12000);

INSERT IGNORE INTO payment (id, created_at, updated_at, amount_cents, bank_txn_id, captured_at, card_brand, card_last4, currency, currency_conversion_rate, fee_cents, gateway, intent_id, meta, payment_method, payment_status, payout_id, payout_status, refunded_at, refunded_cents, settlement_date, settlement_status, total_price, transaction_id, version, booking_id) VALUES
  (1, @now, @now, 12000, 'BANK123', @now, 'VISA', '4242', 'GBP', 1.0, 300, 'RAZORPAY', 'INTENT-1', NULL, 'CARD', 'COMPLETED', NULL, NULL, NULL, 0, NULL, NULL, 120.00, 'TXN-0001', 1, 1);

INSERT IGNORE INTO payment_refund (id, created_at, updated_at, booking_id, payment_id, requested_by, processed_by, amount_cents, currency, reason, status, prefer_voucher, gateway_refund_id, voucher_code, processed_at, meta) VALUES
  (1, @now, @now, 1, 1, 4, 1, 1000, 'GBP', 'Goodwill gesture', 'COMPLETED', b'0', 'GATE-REF-1', 'VCHR-001', @now, NULL);

INSERT IGNORE INTO payout (id, created_at, updated_at, amount_cents, bank_account_masked, currency, fees_cents, meta, payment_ids, payout_date, payout_reference, status, version, spa_id) VALUES
  (1, @now, @now, 9000, 'XXXX1234', 'GBP', 500, NULL, JSON_ARRAY(1), DATE_ADD(@now, INTERVAL 15 DAY), 'PAYOUT-001', 'PENDING', 1, 1);

-- Messaging and notifications
INSERT IGNORE INTO notification_template (id, created_at, updated_at, code, channel, name, subject, body, locale, description, enabled, archived_at) VALUES
  (1, @now, @now, 'BOOKING_CONFIRMED', 'EMAIL', 'Booking confirmation', 'Your booking is confirmed', 'Dear {{name}}, your booking is confirmed.', 'en', 'Sent when booking is confirmed', b'1', NULL);

INSERT IGNORE INTO notification_log (id, created_at, updated_at, channel, destination, error_message, payload, provider_message_id, sent_at, status, template_code, version, user_id, title, message, read_at) VALUES
  (1, @now, @now, 'EMAIL', 'customer@nirvana.test', NULL, JSON_OBJECT('bookingId',1), 'MSG-001', @now, 'SENT', 'BOOKING_CONFIRMED', 1, 4, 'Booking confirmed', 'See you soon at Nirvana Marylebone', NULL);

INSERT IGNORE INTO notifications (id, created_at, updated_at, recipient_user_id, type, title, message, read_at) VALUES
  (1, @now, @now, 2, 'BOOKING', 'New booking received', 'Booking BOOK-0001 has been confirmed', NULL);

INSERT IGNORE INTO message_thread (id, created_at, updated_at, user_id, booking_id, subject, status, last_message_at, unread_count) VALUES
  (1, @now, @now, 4, 1, 'Question about arrival time', 'OPEN', @now, 0);

INSERT IGNORE INTO chat_message (id, created_at, updated_at, thread_id, sender_user_id, sender_type, content, moderation_status, moderation_note, moderated_at) VALUES
  (1, @now, @now, 1, 4, 'USER', 'Can I arrive 10 minutes early?', 'APPROVED', NULL, @now);

INSERT IGNORE INTO message_attachment (id, created_at, updated_at, message_id, file_url, file_name, content_type, size_bytes, moderation_status, moderation_note) VALUES
  (1, @now, @now, 1, 'https://cdn.nirvana/uploads/question.png', 'question.png', 'image/png', 10240, 'APPROVED', NULL);

-- Reviews, FAQ and experiments
INSERT IGNORE INTO review (id, booking_id, user_id, spa_id, rating, title, text, is_visible, created_at, reply_text, reply_by_provider_id, reply_at, helpful_count, reported_count, meta) VALUES
  (1, 1, 4, 1, 5, 'Amazing experience', 'Loved the massage and ambience', b'1', @now, 'Thanks for visiting!', 1, @now, 12, 0, NULL);

INSERT IGNORE INTO faq (id, question, answer, category, is_active, created_at) VALUES
  (1, 'What is your cancellation policy?', 'Free cancellation up to 24 hours before the appointment.', 'Policy', b'1', @now);

INSERT IGNORE INTO content_variant (id, experiment_key, variant_key, content, is_active, created_at, locale) VALUES
  (1, 'booking_cta', 'variant_a', 'Book your relaxation now', b'1', @now, 'en');

-- Media assets for the spa
INSERT IGNORE INTO media_asset (id, created_at, updated_at, entity_id, entity_type, media_type, meta, position, is_primary, url, version) VALUES
  (1, @now, @now, 1, 'SPA', 'IMAGE', NULL, 1, b'1', 'https://cdn.nirvana/hero.jpg', 1);

-- Inventory calendar and analytics
INSERT IGNORE INTO inventory_calendar (id, spa_id, service_id, service_date, available_units, base_price_cents, override_price_cents, currency, locked, note, created_at, updated_at) VALUES
  (1, 1, 1, CURDATE(), 5, 12000, 11000, 'GBP', FALSE, 'Weekday promo', @now, @now);

INSERT IGNORE INTO provider_audit_log (id, spa_id, actor_type, actor_id, action, details, created_at, updated_at) VALUES
  (1, 1, 'MANAGER', 2, 'UPDATED_PRICING', 'Adjusted weekday prices', @now, @now);

INSERT IGNORE INTO support_override (id, spa_id, booking_id, override_type, status, payload, requested_by, resolved_by, resolved_at, resolution_notes, created_at, updated_at) VALUES
  (1, 1, 1, 'REFUND', 'APPROVED', '{"amount":1000}', 'Admin', 'Admin', @now, 'Approved as courtesy', @now, @now);

-- Compliance and localization add-ons
INSERT IGNORE INTO blackout_window (id, spa_id, provider_id, start_ts, end_ts, reason, created_by, created_at, updated_at) VALUES
  (2, 1, NULL, DATE_ADD(@now, INTERVAL 5 DAY), DATE_ADD(DATE_ADD(@now, INTERVAL 5 DAY), INTERVAL 3 HOUR), 'Public holiday', 'Admin', @now, @now);

-- Corporate onboarding
INSERT IGNORE INTO corporate (id, created_at, updated_at, name, domain, contact_person, contact_email, status) VALUES
  (1, @now, @now, 'Acme Corp', 'acme.test', 'Alex Admin', 'admin@acme.test', 'ACTIVE');

INSERT IGNORE INTO corporate_deal (id, created_at, updated_at, corporate_id, deal_name, description, coupon_type, total_sessions_per_employee, global_package_type, start_date, end_date, status, corporate_payment_status) VALUES
  (1, @now, @now, 1, 'Acme Wellness 2025', 'Complimentary wellness sessions for staff', 'SESSION', 3, 'GLOBAL_BRONZE', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 'ACTIVE', 'PAID');

INSERT IGNORE INTO corporate_employee (id, created_at, updated_at, corporate_id, user_id, employee_email, employee_identifier, status) VALUES
  (1, @now, @now, 1, 6, 'employee@corp.test', 'EMP-001', 'ACTIVE');

INSERT IGNORE INTO corporate_employee_coupon (id, created_at, updated_at, corporate_id, corporate_deal_id, corporate_employee_id, user_id, coupon_type, total_sessions, remaining_sessions, global_package_type, start_date, expiry_date, status) VALUES
  (1, @now, @now, 1, 1, 1, 6, 'SESSION', 3, 3, 'GLOBAL_BRONZE', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 'ACTIVE');

INSERT IGNORE INTO corporate_coupon_usage_log (id, created_at, updated_at, corporate_employee_coupon_id, user_id, corporate_id, booking_id, spa_id, usage_datetime, session_number, notes) VALUES
  (1, @now, @now, 1, 6, 1, 1, 1, @now, 1, 'Used during corporate wellness day');

INSERT IGNORE INTO corporate_onboarding_upload (id, created_at, updated_at, corporate_id, corporate_deal_id, original_file_name, status, total_records, success_count, failure_count) VALUES
  (1, @now, @now, 1, 1, 'employees.csv', 'COMPLETED', 50, 50, 0);


-- Favorite tracking
INSERT IGNORE INTO user_favorite_spa (id, created_at, updated_at, version, spa_id, user_id) VALUES
  (1, @now, @now, 1, 1, 4);

INSERT IGNORE INTO user_favorite_therapist (id, created_at, updated_at, version, therapist_id, user_id) VALUES
  (1, @now, @now, 1, 1, 4);
