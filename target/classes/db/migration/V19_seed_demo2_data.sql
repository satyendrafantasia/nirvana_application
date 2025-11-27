-- sql
SET @now = NOW(6);

-- numeric generator: ids 2..101 (100 rows)
WITH nums AS (
  SELECT (ones.n + tens.n * 10 + 2) AS id
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS ones
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS tens
)

-- Roles
INSERT IGNORE INTO role (id, role_type)
SELECT 1000 + id, CONCAT('DEMO_ROLE_', id)
FROM nums;

-- App users (compact set of columns that match the seed schema)
INSERT IGNORE INTO app_user (
  id, created_at, updated_at, active, display_name, email, email_verified, is_active, last_login_at,
  name, password, phone, preferred_contact_method, timezone, username, version, role_id
)
SELECT
  2000 + id,
  @now,
  @now,
  b'1',
  CONCAT('Demo User ', id),
  CONCAT('demo', id, '@nirvana.test'),
  b'1',
  b'1',
  @now,
  CONCAT('Demo', id),
  '{noop}password',
  CONCAT('+9190000', LPAD(id, 4, '0')),
  IF(MOD(id,2)=0,'EMAIL','PHONE'),
  'Asia/Kolkata',
  CONCAT('demo', id),
  1,
  CASE WHEN MOD(id,5)=0 THEN 1 WHEN MOD(id,5)=1 THEN 2 WHEN MOD(id,5)=2 THEN 3 ELSE 2 END
FROM nums;

-- User roles mapping
INSERT IGNORE INTO user_roles (user_id, role)
SELECT 2000 + id, CONCAT('ROLE_DEMO_', MOD(id,5)+1)
FROM nums;

-- Spa manager mapping
INSERT IGNORE INTO spa_manager (id, user_id)
SELECT 3000 + id, 2000 + id
FROM nums;

-- Provider roster (simple)
INSERT IGNORE INTO provider_user (
  id, created_at, updated_at, display_name, email, employee_code, hire_date, is_active, languages, name, phone, rating_avg, rating_count, version
)
SELECT
  4000 + id,
  @now,
  @now,
  CONCAT('Provider ', id),
  CONCAT('provider', id, '@nirvana.test'),
  CONCAT('PR-', LPAD(id,4,'0')),
  DATE_SUB(@now, INTERVAL (id * 10) DAY),
  b'1',
  JSON_ARRAY('English'),
  CONCAT('Provider ', id),
  CONCAT('+91-90000', LPAD(id,4,'0')),
  4.0 + (MOD(id,10) * 0.02),
  10 + id,
  1
FROM nums;

-- Spa catalog (100 spas)
INSERT IGNORE INTO spa (
  id, created_at, updated_at, address_line1, address_line2, address_type, city, country,
  country_code, formatted_address, geo_source, google_place_id, landmark, latitude, locality,
  longitude, meta, postal_code, state, timezone, amenities, business_reg_number, commission_pct,
  default_currency, description, email, established_at, gstin, images, is_active, is_featured, is_verified,
  kyc_approved_at, kyc_status, max_advance_booking_days, max_concurrent_services, min_notice_minutes,
  name, owner_name, phone, rating_avg, rating_count, tags, tax_percent, total_bookings, version, website_url, spa_manager_id
)
SELECT
  5000 + id,
  @now,
  @now,
  CONCAT(id, ' Demo Street'),
  CONCAT('Suite ', LPAD(id,3,'0')),
  'COMMERCIAL',
  CONCAT('City ', MOD(id,20) + 1),
  'India',
  'IN',
  CONCAT(id, ' Demo Street, City ', MOD(id,20)+1),
  'MAPS',
  CONCAT('place_', 5000 + id),
  CONCAT('Landmark ', id),
  12.900000 + (id * 0.01),
  CONCAT('Locality ', MOD(id,15)+1),
  77.500000 + (id * 0.01),
  JSON_OBJECT('parking', IF(MOD(id,2)=0,'valet','street')),
  CONCAT('560', LPAD(id,3,'0')),
  'Karnataka',
  'Asia/Kolkata',
  JSON_ARRAY('Sauna','Steam'),
  CONCAT('BRN-', LPAD(5000 + id,6,'0')),
  18,
  'INR',
  CONCAT('Demo spa location ', id),
  CONCAT('location', id, '@nirvana.test'),
  DATE_SUB(@now, INTERVAL (id * 3) DAY),
  CONCAT('GSTIN', LPAD(5000 + id,8,'0')),
  JSON_ARRAY(CONCAT('https://cdn.nirvana/spa', id, '/hero.jpg')),
  b'1',
  IF(MOD(id,10)=0, b'1', b'0'),
  b'1',
  DATE_SUB(@now, INTERVAL (id * 2) DAY),
  'VERIFIED',
  30,
  3,
  60,
  CONCAT('Nirvana Demo ', id),
  CONCAT('Owner ', id),
  CONCAT('+91-90000', LPAD(id,4,'0')),
  4.0 + (MOD(id,10)*0.02),
  100 + id,
  JSON_ARRAY('demo'),
  18,
  1000 + id,
  1,
  CONCAT('https://nirvana.test/spa/', 5000 + id),
  3000 + id
FROM nums;

-- Spa rooms (one per spa)
INSERT IGNORE INTO spa_room (id, created_at, updated_at, is_active, capacity, code, name, version, spa_id)
SELECT
  6000 + id,
  @now,
  @now,
  b'1',
  IF(MOD(id,3)=0,3,2),
  CONCAT('RM-', LPAD(id,3,'0')),
  CONCAT('Therapy Room ', id),
  1,
  5000 + id
FROM nums;

-- Schedule rules (1 per spa)
INSERT IGNORE INTO schedule_rule (id, applies_from, applies_to, close_local, is_holiday, note, open_local, version, weekday, spa_id)
SELECT
  7000 + id,
  CURDATE(),
  NULL,
  '21:00:00',
  b'0',
  CONCAT('Standard hours for demo ', id),
  '09:00:00',
  1,
  MOD(id,7)+1,
  5000 + id
FROM nums;

-- Services (1 per spa)
INSERT IGNORE INTO service (
  id, created_at, updated_at, base_price_cents, buffer_min, cancellation_policy_json, category, currency,
  description, duration_min, duration_minutes, gender_allowed, images, is_active, is_visible_on_marketplace, max_persons, min_persons, name, price_cents, service_code, sub_category, version, spa_id
)
SELECT
  8000 + id,
  @now,
  @now,
  3000 + (id * 10),
  10,
  JSON_OBJECT('window_hours',24),
  'Massage',
  'INR',
  CONCAT('Demo massage at spa ', id),
  45,
  45,
  'ANY',
  JSON_ARRAY(CONCAT('https://cdn.nirvana/spa', id, '/service.jpg')),
  b'1',
  b'1',
  1,
  1,
  CONCAT('Demo Massage ', id),
  3000 + (id * 10),
  CONCAT('SRV-', LPAD(8000 + id,5,'0')),
  'Relaxation',
  1,
  5000 + id
FROM nums;

-- Therapists (1 per spa)
INSERT IGNORE INTO therapist (
  id, created_at, updated_at, bio, certifications, country_of_origin, currency, display_name, email, experience_years, gender, hourly_rate_cents, images, is_active, is_available, languages, last_seen_at, name, phone, rating_avg, rating_count, type, version, service_id, spa_id
)
SELECT
  9000 + id,
  @now,
  @now,
  CONCAT('Therapist for spa ', id, ' demo bio.'),
  JSON_ARRAY('Swedish Massage'),
  'India',
  'INR',
  CONCAT('Therapist ', id),
  CONCAT('therapist', id, '@nirvana.test'),
  MOD(id,15)+1,
  IF(MOD(id,2)=0,'FEMALE','MALE'),
  2000 + (id * 5),
  JSON_ARRAY(CONCAT('https://cdn.nirvana/therapists/', id, '.jpg')),
  b'1',
  b'1',
  JSON_ARRAY('English','Hindi'),
  DATE_SUB(@now, INTERVAL (id % 10) HOUR),
  CONCAT('Therapist ', id),
  CONCAT('+91-90', LPAD(id,6,'0')),
  4.0 + (MOD(id,10) * 0.03),
  10 + id,
  'STAFF',
  1,
  8000 + id,
  5000 + id
FROM nums;

-- Therapist service mapping
INSERT IGNORE INTO therapist_service (therapist_id, service_id)
SELECT 9000 + id, 8000 + id
FROM nums;

-- Slots (availability) — one slot per spa
INSERT IGNORE INTO slot (
  id, created_at, updated_at, booked_units, capacity_unit, end_ts, start_ts, status, version, assigned_provider_user_id, service_id, spa_id, room_id
)
SELECT
  10000 + id,
  @now,
  @now,
  0,
  1,
  DATE_ADD(DATE_ADD(@now, INTERVAL id HOUR), INTERVAL 1 HOUR),
  DATE_ADD(@now, INTERVAL id HOUR),
  'OPEN',
  1,
  4000 + id,
  8000 + id,
  5000 + id,
  6000 + id
FROM nums;

-- Bookings (one per spa)
INSERT IGNORE INTO booking (
  id, created_at, updated_at, booking_reference, channel, currency, customer_notes, deposit_cents, end_ts, guest_count, ip_address, items, last_status_changed_at, price_cents, provider_notes, remainder_cents, scheduled_by, start_ts, status, tax_breakdown, tax_cents, tip_cents, version, provider_assigned_id, therapist_id, therapist_type, service_id, slot_id, spa_id, user_id, payment_mode, payment_type, payment_source_type
)
SELECT
  11000 + id,
  @now,
  @now,
  CONCAT('BOOK-DEMO-', LPAD(id,4,'0')),
  'WEB',
  'INR',
  CONCAT('Demo booking notes ', id),
  1000,
  DATE_ADD(DATE_ADD(@now, INTERVAL id HOUR), INTERVAL 1 HOUR),
  1,
  '127.0.0.1',
  JSON_OBJECT('services', JSON_ARRAY(8000 + id)),
  @now,
  5000 + (id * 5),
  CONCAT('Provider note ', id),
  0,
  'USER',
  DATE_ADD(@now, INTERVAL id HOUR),
  'CONFIRMED',
  JSON_OBJECT('tax_rate', 0.18),
  900 + id,
  0,
  1,
  4000 + id,
  9000 + id,
  'GENERAL',
  8000 + id,
  10000 + id,
  5000 + id,
  2000 + id,
  'ONLINE',
  'STANDARD',
  'NORMAL'
FROM nums;

-- Booking events
INSERT IGNORE INTO booking_event (id, actor_id, actor_type, created_at, event_payload, new_status, version, booking_id)
SELECT 12000 + id, 2000 + MOD(id,50), 'CUSTOMER', @now, JSON_OBJECT('note', CONCAT('Auto event ', id)), 'CONFIRMED', 1, 11000 + id
FROM nums;

-- Payments
INSERT IGNORE INTO payment (
  id, created_at, updated_at, amount_cents, card_brand, card_last4, currency, gateway, payment_method, payment_status, total_price, transaction_id, version, booking_id
)
SELECT
  13000 + id,
  @now,
  @now,
  5000 + (id * 5),
  'VISA',
  '4242',
  'INR',
  'RAZORPAY',
  'CARD',
  'COMPLETED',
  (5000 + (id * 5)) / 100,
  CONCAT('TXN-DEMO-', LPAD(id,5,'0')),
  1,
  11000 + id
FROM nums;

-- Invoices
INSERT IGNORE INTO invoice (
  id, created_at, updated_at, currency, discount_cents, due_at, invoice_number, issued_at, status, tax_cents, version, booking_id, spa_id, user_id, amount_cents, total_cents
)
SELECT
  14000 + id,
  @now,
  @now,
  'INR',
  100,
  DATE_ADD(@now, INTERVAL 7 DAY),
  CONCAT('INV-DEMO-', LPAD(id,5,'0')),
  @now,
  'ISSUED',
  90,
  1,
  11000 + id,
  5000 + id,
  2000 + id,
  5000 + (id * 5),
  5000 + (id * 5)
FROM nums;

-- Reviews
INSERT IGNORE INTO review (id, booking_id, user_id, spa_id, rating, title, text, is_visible, created_at)
SELECT
  15000 + id,
  11000 + id,
  2000 + id,
  5000 + id,
  MOD(id,5)+1,
  CONCAT('Title ', id),
  CONCAT('Demo review text for spa ', id),
  b'1',
  @now
FROM nums;

-- Media assets
INSERT IGNORE INTO media_asset (id, created_at, updated_at, entity_id, entity_type, media_type, position, is_primary, url, version)
SELECT
  16000 + id,
  @now,
  @now,
  5000 + id,
  'SPA',
  'IMAGE',
  1,
  b'1',
  CONCAT('https://cdn.nirvana/demo/spa', id, '/img.jpg'),
  1
FROM nums;

-- Notification logs
INSERT IGNORE INTO notification_log (id, created_at, updated_at, channel, destination, payload, provider_message_id, sent_at, status, template_code, version, user_id, title, message)
SELECT
  17000 + id,
  @now,
  @now,
  'EMAIL',
  CONCAT('demo', id, '@nirvana.test'),
  JSON_OBJECT('bookingId', 11000 + id),
  CONCAT('MSG-DEMO-', id),
  @now,
  'SENT',
  'BOOKING_CONFIRMED',
  1,
  2000 + id,
  'Booking confirmed',
  CONCAT('Your demo booking ', id, ' is confirmed.')
FROM nums;

-- Messages / threads
INSERT IGNORE INTO message_thread (id, created_at, updated_at, user_id, booking_id, subject, status, last_message_at, unread_count)
SELECT
  18000 + id,
  @now,
  @now,
  2000 + id,
  11000 + id,
  CONCAT('Question about booking ', id),
  'OPEN',
  @now,
  0
FROM nums;

INSERT IGNORE INTO chat_message (id, created_at, updated_at, thread_id, sender_user_id, sender_type, content, moderation_status, moderated_at)
SELECT
  19000 + id,
  @now,
  @now,
  18000 + id,
  2000 + id,
  'USER',
  CONCAT('Can I reschedule demo booking ', id, '?'),
  'APPROVED',
  @now
FROM nums;

INSERT IGNORE INTO message_attachment (id, created_at, updated_at, message_id, file_url, file_name, content_type, size_bytes, moderation_status)
SELECT
  20000 + id,
  @now,
  @now,
  19000 + id,
  CONCAT('https://cdn.nirvana/demo/attachment', id, '.png'),
  CONCAT('attach', id, '.png'),
  'image/png',
  1024,
  'APPROVED'
FROM nums;

-- Booking hold and waitlist
INSERT IGNORE INTO booking_hold (id, user_id, spa_id, slot_id, services_json, hold_token, expires_at, hold_units, created_at, updated_at, version)
SELECT
  21000 + id,
  2000 + id,
  5000 + id,
  10000 + id,
  JSON_ARRAY(8000 + id),
  CONCAT('HOLD-DEMO-', id),
  DATE_ADD(@now, INTERVAL 30 MINUTE),
  1,
  @now,
  @now,
  1
FROM nums;

INSERT IGNORE INTO waitlist_entry (id, user_id, spa_id, service_id, guests, contact_email, contact_phone, active, created_at, updated_at, version)
SELECT
  22000 + id,
  2000 + id,
  5000 + id,
  8000 + id,
  1,
  CONCAT('demo', id, '@nirvana.test'),
  CONCAT('+9190000', LPAD(id,4,'0')),
  TRUE,
  @now,
  @now,
  1
FROM nums;

-- Memberships / packages
INSERT IGNORE INTO membership_plan (id, created_at, updated_at, is_active, benefits, currency, description, duration_days, name, price_cents, version, spa_id)
SELECT
  23000 + id,
  @now,
  @now,
  b'1',
  JSON_OBJECT('free_sessions',1),
  'INR',
  CONCAT('Demo plan ', id),
  30,
  CONCAT('DemoPlan ', id),
  1000 + (id*10),
  1,
  5000 + id
FROM nums;

INSERT IGNORE INTO user_membership (id, created_at, updated_at, end_at, start_at, status, version, plan_id, spa_id, user_id)
SELECT
  24000 + id,
  @now,
  @now,
  DATE_ADD(@now, INTERVAL 30 DAY),
  @now,
  'ACTIVE',
  1,
  23000 + id,
  5000 + id,
  2000 + id
FROM nums;

INSERT IGNORE INTO packages (id, created_at, updated_at, package_type, price_cents, session_count, description, validity_days, is_active)
SELECT
  25000 + id,
  @now,
  @now,
  'DEMO_BRONZE',
  2000 + id,
  3,
  CONCAT('Demo package ', id),
  180,
  1
FROM nums;

INSERT IGNORE INTO service_package_spa (package_id, spa_id)
SELECT 25000 + id, 5000 + id FROM nums;

INSERT IGNORE INTO user_package_subscription (
  id, created_at, updated_at, user_id, package_id, package_type, purchase_date, expiry_date, remaining_sessions, status, payment_status, version
)
SELECT
  26000 + id,
  @now,
  @now,
  2000 + id,
  25000 + id,
  'DEMO_BRONZE',
  @now,
  DATE_ADD(@now, INTERVAL 6 MONTH),
  3,
  'ACTIVE',
  'PAID',
  1
FROM nums;

-- Coupons & vouchers
INSERT IGNORE INTO coupon (id, created_at, updated_at, is_active, applies_to_services, code, discount_type, discount_value, max_uses_global, max_uses_per_user, min_order_cents, valid_from, valid_to, version, spa_id)
SELECT
  27000 + id,
  @now,
  @now,
  b'1',
  JSON_ARRAY(8000 + id),
  CONCAT('DEMO', LPAD(id,4,'0')),
  'PERCENTAGE',
  10,
  100,
  1,
  500,
  DATE_SUB(@now, INTERVAL 7 DAY),
  DATE_ADD(@now, INTERVAL 30 DAY),
  1,
  5000 + id
FROM nums;

INSERT IGNORE INTO voucher (id, created_at, updated_at, code, user_id, amount_cents, currency, note, issued_by, expires_at, version)
SELECT
  28000 + id,
  @now,
  @now,
  CONCAT('VCHR-DEMO-', LPAD(id,4,'0')),
  2000 + id,
  1000,
  'INR',
  'Demo voucher',
  'Admin',
  DATE_ADD(@now, INTERVAL 90 DAY),
  1
FROM nums;

-- Operational controls and logging
INSERT IGNORE INTO closure (id, start_ts, end_ts, is_recurring, reason, scope, type, version, spa_id)
SELECT
  29000 + id,
  @now,
  DATE_ADD(@now, INTERVAL 1 DAY),
  b'0',
  'Demo maintenance',
  'FULL',
  'MAINTENANCE',
  1,
  5000 + id
FROM nums;

INSERT IGNORE INTO blackout_window (id, spa_id, provider_id, start_ts, end_ts, reason, created_by, created_at, updated_at)
SELECT
  30000 + id,
  5000 + id,
  4000 + id,
  DATE_ADD(@now, INTERVAL (id+2) DAY),
  DATE_ADD(DATE_ADD(@now, INTERVAL (id+2) DAY), INTERVAL 2 HOUR),
  'Demo training',
  CONCAT('Manager ', id),
  @now,
  @now
FROM nums;

INSERT IGNORE INTO provider_leave (id, created_at, updated_at, end_ts, reason, start_ts, status, version, approved_by_user_id, provider_user_id, spa_id)
SELECT
  31000 + id,
  @now,
  @now,
  DATE_ADD(@now, INTERVAL 2 DAY),
  'Medical leave',
  DATE_ADD(@now, INTERVAL 1 DAY),
  'APPROVED',
  1,
  2000 + MOD(id,10),
  4000 + id,
  5000 + id
FROM nums;

INSERT IGNORE INTO admin_action_log (id, created_at, updated_at, action_type, after_state, entity_id, entity_type, reason, version, actor_user_id)
SELECT
  32000 + id,
  @now,
  @now,
  'UPDATE',
  JSON_OBJECT('name', CONCAT('Demo entity ', id)),
  5000 + id,
  'spa',
  'Bulk seed',
  1,
  2000 + MOD(id,10)
FROM nums;

INSERT IGNORE INTO inventory_calendar (id, spa_id, service_id, service_date, available_units, base_price_cents, override_price_cents, currency, created_at, updated_at)
SELECT
  33000 + id,
  5000 + id,
  8000 + id,
  CURDATE(),
  5,
  3000 + id,
  NULL,
  'INR',
  @now,
  @now
FROM nums;

-- Corporate demo data
INSERT IGNORE INTO corporate (id, created_at, updated_at, name, domain, contact_person, contact_email, status)
SELECT
  34000 + id,
  @now,
  @now,
  CONCAT('Demo Corp ', id),
  CONCAT('demo', id, '.test'),
  CONCAT('Contact ', id),
  CONCAT('contact', id, '@demo.test'),
  'ACTIVE'
FROM nums;

INSERT IGNORE INTO corporate_deal (id, created_at, updated_at, corporate_id, deal_name, description, coupon_type, total_sessions_per_employee, global_package_type, start_date, end_date, status)
SELECT
  35000 + id,
  @now,
  @now,
  34000 + id,
  CONCAT('Corp Deal ', id),
  'Demo corporate deal',
  'SESSION',
  2,
  'DEMO_BRONZE',
  CURDATE(),
  DATE_ADD(CURDATE(), INTERVAL 1 YEAR),
  'ACTIVE'
FROM nums;

INSERT IGNORE INTO corporate_employee (id, created_at, updated_at, corporate_id, user_id, employee_email, employee_identifier, status)
SELECT
  36000 + id,
  @now,
  @now,
  34000 + id,
  2000 + id,
  CONCAT('emp', id, '@corp.test'),
  CONCAT('EMP-', LPAD(id,4,'0')),
  'ACTIVE'
FROM nums;

INSERT IGNORE INTO corporate_employee_coupon (id, created_at, updated_at, corporate_id, corporate_deal_id, corporate_employee_id, user_id, coupon_type, total_sessions, remaining_sessions, global_package_type, start_date, expiry_date, status)
SELECT
  37000 + id,
  @now,
  @now,
  34000 + id,
  35000 + id,
  36000 + id,
  2000 + id,
  'SESSION',
  2,
  2,
  'DEMO_BRONZE',
  CURDATE(),
  DATE_ADD(CURDATE(), INTERVAL 1 YEAR),
  'ACTIVE'
FROM nums;

INSERT IGNORE INTO corporate_coupon_usage_log (id, created_at, updated_at, corporate_employee_coupon_id, user_id, corporate_id, booking_id, spa_id, usage_datetime, session_number, notes)
SELECT
  38000 + id,
  @now,
  @now,
  37000 + id,
  2000 + id,
  34000 + id,
  11000 + id,
  5000 + id,
  @now,
  1,
  'Used demo session'
FROM nums;

INSERT IGNORE INTO corporate_onboarding_upload (id, created_at, updated_at, corporate_id, corporate_deal_id, original_file_name, status, total_records, success_count, failure_count)
SELECT
  39000 + id,
  @now,
  @now,
  34000 + id,
  35000 + id,
  CONCAT('employees_', id, '.csv'),
  'COMPLETED',
  50,
  50,
  0
FROM nums;

-- Favorites tracking
INSERT IGNORE INTO user_favorite_spa (id, created_at, updated_at, version, spa_id, user_id)
SELECT
  40000 + id,
  @now,
  @now,
  1,
  5000 + id,
  2000 + id
FROM nums;

INSERT IGNORE INTO user_favorite_therapist (id, created_at, updated_at, version, therapist_id, user_id)
SELECT
  41000 + id,
  @now,
  @now,
  1,
  9000 + id,
  2000 + id
FROM nums;
