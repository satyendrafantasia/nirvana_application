CREATE TABLE standard_room_service (
    room_id BIGINT PRIMARY KEY, service_id BIGINT NOT NULL,
    FOREIGN KEY(room_id) REFERENCES spa_room(id), FOREIGN KEY(service_id) REFERENCES service(id)
);

CREATE TEMPORARY TABLE standard_import (id BIGINT PRIMARY KEY);
INSERT INTO standard_import
SELECT DISTINCT s.id FROM spa s JOIN spa_lead l ON l.spa_id=s.id
WHERE l.status='ONBOARDED' AND l.permission_confirmed_at IS NOT NULL
  AND s.is_active=0 AND s.kyc_status='PENDING' AND s.spa_manager_id IS NULL
  AND JSON_EXTRACT(s.meta,'$.onboardedFromLead') IS NOT NULL;

CREATE TEMPORARY TABLE standard_menu (n INT PRIMARY KEY, name VARCHAR(80), minutes INT, cents INT);
INSERT INTO standard_menu VALUES
 (1,'Swedish Massage',60,200000),(2,'Deep Tissue Massage',60,200000),(3,'Aromatherapy Massage',60,200000),
 (4,'Foot Massage',45,150000),(5,'Head and Shoulder Massage',45,150000);

INSERT INTO service (created_at,updated_at,spa_id,name,description,category,duration_min,duration_minutes,buffer_min,base_price_cents,price_cents,currency,gender_allowed,is_active,is_visible_on_marketplace,min_persons,max_persons,version)
SELECT UTC_TIMESTAMP(),UTC_TIMESTAMP(),i.id,m.name,m.name,'massage',m.minutes,m.minutes,0,m.cents,m.cents,'INR','ANY',1,1,1,1,0
FROM standard_import i CROSS JOIN standard_menu m
LEFT JOIN service v ON v.spa_id=i.id AND v.name=m.name WHERE v.id IS NULL;
UPDATE service v JOIN standard_import i ON i.id=v.spa_id JOIN standard_menu m ON v.name=m.name
SET v.base_price_cents=m.cents,v.price_cents=m.cents WHERE v.duration_min=m.minutes;

INSERT INTO spa_room(created_at,updated_at,spa_id,name,capacity,is_active,version)
SELECT UTC_TIMESTAMP(),UTC_TIMESTAMP(),i.id,CONCAT('Treatment room ',m.n),1,1,0 FROM standard_import i CROSS JOIN standard_menu m
LEFT JOIN spa_room r ON r.spa_id=i.id AND r.name=CONCAT('Treatment room ',m.n) WHERE r.id IS NULL;
INSERT INTO therapist(created_at,updated_at,spa_id,name,display_name,type,currency,is_active,is_available,rating_avg,rating_count,version)
SELECT UTC_TIMESTAMP(),UTC_TIMESTAMP(),i.id,CONCAT('Therapist ',m.n),CONCAT('Therapist ',m.n),'massage','INR',1,1,0,0,0 FROM standard_import i CROSS JOIN standard_menu m
LEFT JOIN therapist t ON t.spa_id=i.id AND t.name=CONCAT('Therapist ',m.n) WHERE t.id IS NULL;
INSERT IGNORE INTO therapist_service(therapist_id,service_id)
SELECT t.id,v.id FROM standard_import i JOIN therapist t ON t.spa_id=i.id JOIN service v ON v.spa_id=i.id
JOIN standard_menu m ON m.name=v.name WHERE t.name IN ('Therapist 1','Therapist 2','Therapist 3','Therapist 4','Therapist 5');
INSERT INTO standard_room_service(room_id,service_id)
SELECT r.id,v.id FROM standard_import i JOIN service v ON v.spa_id=i.id JOIN standard_menu m ON m.name=v.name
JOIN spa_room r ON r.spa_id=i.id AND r.name=CONCAT('Treatment room ',m.n);

DELETE r FROM schedule_rule r JOIN standard_import i ON i.id=r.spa_id WHERE r.note='From Google Maps at onboarding';
INSERT IGNORE INTO spa_working_day(spa_id,day_of_week)
SELECT i.id,d.day_name FROM standard_import i CROSS JOIN (
SELECT 'MONDAY' day_name UNION ALL SELECT 'TUESDAY' UNION ALL SELECT 'WEDNESDAY' UNION ALL SELECT 'THURSDAY' UNION ALL SELECT 'FRIDAY' UNION ALL SELECT 'SATURDAY' UNION ALL SELECT 'SUNDAY') d;
UPDATE spa s JOIN standard_import i ON i.id=s.id SET s.open_time_local='11:00',s.close_time_local='21:00',
s.max_concurrent_services=5,s.is_active=1,s.is_verified=1,s.kyc_status='APPROVED',s.kyc_approved_at=UTC_TIMESTAMP(),
s.meta=JSON_SET(s.meta,'$.standardInventory',true);
DROP TEMPORARY TABLE standard_menu;
DROP TEMPORARY TABLE standard_import;
