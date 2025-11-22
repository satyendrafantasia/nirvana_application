-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: nirvana_v1
-- ------------------------------------------------------
-- Server version	8.4.7

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin_action_log`
--

DROP TABLE IF EXISTS `admin_action_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_action_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `action_type` enum('CREATE','DELETE','OTHER','STATUS_CHANGE','UPDATE') NOT NULL,
  `after_state` json DEFAULT NULL,
  `before_state` json DEFAULT NULL,
  `entity_id` bigint NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `actor_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_entity` (`entity_type`,`entity_id`),
  KEY `idx_admin_actor` (`actor_user_id`,`created_at`),
  CONSTRAINT `FKfjp101onmmu8gsc4l23bvwfv5` FOREIGN KEY (`actor_user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_user`
--

DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `email_verified` bit(1) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `locale` varchar(255) DEFAULT NULL,
  `loyalty_points` int NOT NULL,
  `marketing_opt_in` bit(1) NOT NULL,
  `meta` json DEFAULT NULL,
  `mfa_enabled` bit(1) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `password_salt` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `phone_verified` bit(1) NOT NULL,
  `preferred_contact_method` varchar(255) DEFAULT NULL,
  `profile_image_url` varchar(1000) DEFAULT NULL,
  `referral_code` varchar(255) DEFAULT NULL,
  `referred_by` varchar(255) DEFAULT NULL,
  `timezone` varchar(255) NOT NULL,
  `username` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1j9d9a06i600gd43uu3km82jw` (`email`),
  UNIQUE KEY `UKexslcon9jmfy0xhclbtpf26vo` (`phone`),
  UNIQUE KEY `UK5mgj4c5y3hr1hrgerihmkob72` (`referral_code`),
  KEY `idx_appuser_email` (`email`),
  KEY `idx_appuser_phone` (`phone`),
  KEY `idx_appuser_referral` (`referral_code`),
  KEY `FK49hx9nj6onfot1fxtonj986ab` (`role_id`),
  CONSTRAINT `FK49hx9nj6onfot1fxtonj986ab` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking`
--

DROP TABLE IF EXISTS `booking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `arrived_at` datetime(6) DEFAULT NULL,
  `booking_reference` varchar(64) NOT NULL,
  `cancellation_reason_code` varchar(100) DEFAULT NULL,
  `cancellation_reason_text` varchar(1000) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancelled_by` enum('ADMIN','SPA','SYSTEM','USER') DEFAULT NULL,
  `channel` enum('API','KIOSK','MOBILE','PHONE','WEB') DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `coupon_code` varchar(64) DEFAULT NULL,
  `currency` varchar(10) NOT NULL,
  `customer_notes` varchar(2000) DEFAULT NULL,
  `deposit_cents` int NOT NULL,
  `discount_cents` int NOT NULL,
  `end_ts` datetime(6) NOT NULL,
  `external_booking_id` varchar(128) DEFAULT NULL,
  `guest_count` int NOT NULL,
  `invoice_url` varchar(1000) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `is_test_booking` bit(1) NOT NULL,
  `items` json DEFAULT NULL,
  `last_status_changed_at` datetime(6) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `no_show_marked_at` datetime(6) DEFAULT NULL,
  `policy_snapshot` json NOT NULL,
  `price_cents` int NOT NULL,
  `provider_notes` varchar(2000) DEFAULT NULL,
  `rating_given` bit(1) NOT NULL,
  `refund_status` enum('APPROVED','COMPLETED','NONE','REJECTED','REQUESTED') NOT NULL,
  `remainder_cents` int NOT NULL,
  `reschedule_count` int NOT NULL,
  `scheduled_by` enum('ADMIN','SPA','SYSTEM','USER') DEFAULT NULL,
  `start_ts` datetime(6) NOT NULL,
  `status` enum('ARRIVED','CANCELLED','COMPLETED','CONFIRMED','NO_SHOW','PENDING_PAYMENT') NOT NULL,
  `tax_breakdown` json DEFAULT NULL,
  `tax_cents` int NOT NULL,
  `tip_cents` int NOT NULL,
  `version` bigint DEFAULT NULL,
  `provider_assigned_id` bigint DEFAULT NULL,
  `service_id` bigint NOT NULL,
  `slot_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `user_membership_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_booking_ref` (`booking_reference`),
  UNIQUE KEY `idx_booking_slot` (`slot_id`),
  KEY `idx_booking_user` (`user_id`,`created_at` DESC),
  KEY `idx_booking_spa` (`spa_id`,`created_at` DESC),
  KEY `idx_booking_spa_status_start` (`spa_id`,`status`,`start_ts`),
  KEY `FKbdrn6m3sqyv31cbtnmi18k89s` (`provider_assigned_id`),
  KEY `FKcebnlefwi9r13txu8btclnmsu` (`service_id`),
  KEY `FK7fkb6ko1k4q1loj2kjjrgbymc` (`coupon_id`),
  KEY `FKrjh4mdl00rshmh17vkqjrdib1` (`user_membership_id`),
  CONSTRAINT `FK7fkb6ko1k4q1loj2kjjrgbymc` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`),
  CONSTRAINT `FKbdrn6m3sqyv31cbtnmi18k89s` FOREIGN KEY (`provider_assigned_id`) REFERENCES `provider_user` (`id`),
  CONSTRAINT `FKcebnlefwi9r13txu8btclnmsu` FOREIGN KEY (`service_id`) REFERENCES `service` (`id`),
  CONSTRAINT `FKjwkevjmp88ffehdgyx3hcabeg` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKmf21b3es5u32ktfv3ibpbtoba` FOREIGN KEY (`slot_id`) REFERENCES `slot` (`id`),
  CONSTRAINT `FKn7phgawjwo673xgim9d2cvfe1` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `FKrjh4mdl00rshmh17vkqjrdib1` FOREIGN KEY (`user_membership_id`) REFERENCES `user_membership` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_event`
--

DROP TABLE IF EXISTS `booking_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` bigint DEFAULT NULL,
  `actor_type` enum('ADMIN','CUSTOMER','Spa_MANAGER') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_payload` json DEFAULT NULL,
  `new_status` enum('ARRIVED','CANCELLED','COMPLETED','CONFIRMED','NO_SHOW','PENDING_PAYMENT') NOT NULL,
  `prev_status` enum('ARRIVED','CANCELLED','COMPLETED','CONFIRMED','NO_SHOW','PENDING_PAYMENT') DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_booking_event_lookup` (`booking_id`,`created_at`),
  KEY `idx_booking_event_type` (`new_status`,`actor_type`),
  CONSTRAINT `FKdium3qqd8c30wbis0j5b7gh6o` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_hold`
--

DROP TABLE IF EXISTS `booking_hold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_hold` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `converted_to_booking` bit(1) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `hold_token` varchar(128) NOT NULL,
  `meta` json DEFAULT NULL,
  `services_json` json DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `slot_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKto8i2x9geyfdpoa29yyqk585m` (`hold_token`),
  KEY `idx_hold_user` (`user_id`,`expires_at`),
  KEY `idx_hold_slot` (`slot_id`,`expires_at`),
  KEY `FKjjomhvlxk1559wrw7vswc9gte` (`spa_id`),
  CONSTRAINT `FKjjomhvlxk1559wrw7vswc9gte` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKo9frmu2io3tivqt2jij44rg05` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `FKp0o5opnykf7indu2r3okvgt22` FOREIGN KEY (`slot_id`) REFERENCES `slot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `closure`
--

DROP TABLE IF EXISTS `closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `closure` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_ts` datetime(6) NOT NULL,
  `is_recurring` bit(1) NOT NULL,
  `meta` json DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `rrule` varchar(1000) DEFAULT NULL,
  `scope` varchar(200) DEFAULT NULL,
  `start_ts` datetime(6) NOT NULL,
  `type` varchar(64) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_closure_window` (`spa_id`,`start_ts`,`end_ts`),
  KEY `idx_closure_type` (`spa_id`,`type`,`start_ts`),
  CONSTRAINT `FKjma6mjiv4nyqhpm7d65fht8pd` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coupon`
--

DROP TABLE IF EXISTS `coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `applies_to_services` json DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `discount_type` enum('FLAT','PERCENTAGE') NOT NULL,
  `discount_value` int NOT NULL,
  `max_discount_cents` int DEFAULT NULL,
  `max_uses_global` int DEFAULT NULL,
  `max_uses_per_user` int DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `min_order_cents` int DEFAULT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) NOT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_coupon_code` (`code`),
  KEY `idx_coupon_validity` (`valid_from`,`valid_to`),
  KEY `FKpsw02rk3px52ylf73qqil0lcd` (`spa_id`),
  CONSTRAINT `FKpsw02rk3px52ylf73qqil0lcd` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `invoice`
--

DROP TABLE IF EXISTS `invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount_subtotal` decimal(38,2) NOT NULL,
  `amount_total` decimal(38,2) NOT NULL,
  `currency` varchar(8) NOT NULL,
  `discount_amount` decimal(38,2) NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `invoice_number` varchar(64) NOT NULL,
  `invoice_pdf_url` varchar(1000) DEFAULT NULL,
  `issued_at` datetime(6) NOT NULL,
  `meta` json DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `tax_amount` decimal(38,2) NOT NULL,
  `version` bigint DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_invoice_number` (`invoice_number`),
  KEY `idx_invoice_booking` (`booking_id`),
  KEY `FKjbhlete50obdsgohek2ohrjwr` (`spa_id`),
  KEY `FK260a4ap5h3y3txnyegq9k0j97` (`user_id`),
  CONSTRAINT `FK260a4ap5h3y3txnyegq9k0j97` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `FK4jd6uuk7w0d72riyre2w14fl7` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`id`),
  CONSTRAINT `FKjbhlete50obdsgohek2ohrjwr` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `media_asset`
--

DROP TABLE IF EXISTS `media_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `media_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `entity_id` bigint NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `media_type` enum('IMAGE','VIDEO') NOT NULL,
  `meta` json DEFAULT NULL,
  `position` int DEFAULT NULL,
  `is_primary` bit(1) NOT NULL,
  `url` varchar(1000) NOT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_media_entity` (`entity_type`,`entity_id`,`position`),
  KEY `FKlivdkahhw45bl64a262g6wx42` (`entity_id`),
  CONSTRAINT `FKlivdkahhw45bl64a262g6wx42` FOREIGN KEY (`entity_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `membership_plan`
--

DROP TABLE IF EXISTS `membership_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `membership_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `benefits` json DEFAULT NULL,
  `currency` varchar(8) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `duration_days` int NOT NULL,
  `meta` json DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `price_cents` int NOT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_membership_spa` (`spa_id`,`is_active`),
  CONSTRAINT `FKmm0jxvysdb36l8egg9bqxufu1` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_log`
--

DROP TABLE IF EXISTS `notification_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `channel` enum('EMAIL','PUSH','SMS','WHATSAPP') NOT NULL,
  `destination` varchar(255) NOT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `payload` json DEFAULT NULL,
  `provider_message_id` varchar(255) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','QUEUED','SENT') NOT NULL,
  `template_code` varchar(128) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_user` (`user_id`,`created_at`),
  KEY `idx_notification_channel` (`channel`,`status`),
  CONSTRAINT `FK52nt0xb1g5m8yd0tpmct7lxbr` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment`
--

DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount_cents` int NOT NULL,
  `bank_txn_id` varchar(255) DEFAULT NULL,
  `captured_at` datetime(6) DEFAULT NULL,
  `card_brand` varchar(255) DEFAULT NULL,
  `card_last4` varchar(255) DEFAULT NULL,
  `currency` varchar(255) NOT NULL,
  `currency_conversion_rate` double DEFAULT NULL,
  `fee_cents` int NOT NULL,
  `gateway` varchar(255) NOT NULL,
  `intent_id` varchar(255) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `payment_status` enum('AUTHORIZED','CANCELLED','CAPTURED','COMPLETED','FAILED','INIT','PARTIALLY_REFUNDED','PENDING','REFUNDED') NOT NULL,
  `payout_id` varchar(255) DEFAULT NULL,
  `payout_status` varchar(255) DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `refunded_cents` int NOT NULL,
  `settlement_date` datetime(6) DEFAULT NULL,
  `settlement_status` varchar(255) DEFAULT NULL,
  `total_price` decimal(38,2) NOT NULL,
  `transaction_id` varchar(255) NOT NULL,
  `version` bigint DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtacis04bqalsngo46yvxlo7yb` (`transaction_id`),
  UNIQUE KEY `UKku02qy6369hn9uhy3n7jk9v6e` (`booking_id`),
  UNIQUE KEY `uk_payment_intent` (`intent_id`),
  KEY `idx_payment_booking` (`booking_id`),
  CONSTRAINT `FKqewrl4xrv9eiad6eab3aoja65` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payout`
--

DROP TABLE IF EXISTS `payout`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount_cents` int NOT NULL,
  `bank_account_masked` varchar(255) DEFAULT NULL,
  `currency` varchar(8) NOT NULL,
  `fees_cents` int DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `payment_ids` json DEFAULT NULL,
  `payout_date` datetime(6) DEFAULT NULL,
  `payout_reference` varchar(128) DEFAULT NULL,
  `status` enum('COMPLETED','FAILED','IN_PROGRESS','PENDING') NOT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi6tv0wedinwea95130cckbhl8` (`payout_reference`),
  KEY `idx_payout_spa` (`spa_id`,`payout_date`),
  KEY `idx_payout_status` (`status`),
  CONSTRAINT `FK7o5pkyahtlissxsrwcb6fcv0` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `provider_leave`
--

DROP TABLE IF EXISTS `provider_leave`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_leave` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `end_ts` datetime(6) NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `start_ts` datetime(6) NOT NULL,
  `status` enum('APPROVED','CANCELLED','REJECTED','REQUESTED') NOT NULL,
  `version` bigint DEFAULT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `provider_user_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_leave_provider` (`provider_user_id`,`start_ts`,`end_ts`),
  KEY `FKs2ayabxs3lx5rcg5c5if27bns` (`approved_by_user_id`),
  KEY `FK4llxhim6fe2p6itmr08nk2t0s` (`spa_id`),
  CONSTRAINT `FK4llxhim6fe2p6itmr08nk2t0s` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKrxdt10n5oytp1tma7clsxwbp2` FOREIGN KEY (`provider_user_id`) REFERENCES `provider_user` (`id`),
  CONSTRAINT `FKs2ayabxs3lx5rcg5c5if27bns` FOREIGN KEY (`approved_by_user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `provider_user`
--

DROP TABLE IF EXISTS `provider_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `bank_account_masked` varchar(255) DEFAULT NULL,
  `certifications` json DEFAULT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `employee_code` varchar(255) DEFAULT NULL,
  `hire_date` datetime(6) DEFAULT NULL,
  `id_doc_url` varchar(255) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `languages` json DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `payout_method` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `rating_avg` float DEFAULT NULL,
  `rating_count` int DEFAULT NULL,
  `termination_date` datetime(6) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `work_hours_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbhft69o7ll5vo2f6amm4lgob0` (`email`),
  UNIQUE KEY `UKrl0r4b4qmcjnu84ramo7dqbri` (`employee_code`),
  UNIQUE KEY `UKon47lrruglxex0x7ifm2rr8l3` (`phone`),
  KEY `idx_provider_email` (`email`),
  KEY `idx_provider_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review`
--

DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `helpful_count` int NOT NULL,
  `is_visible` bit(1) NOT NULL,
  `meta` json DEFAULT NULL,
  `rating` smallint NOT NULL,
  `reply_at` datetime(6) DEFAULT NULL,
  `reply_by_provider_id` bigint DEFAULT NULL,
  `reply_text` varchar(2000) DEFAULT NULL,
  `reported_count` int NOT NULL,
  `text` varchar(4000) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm685o801uf70i84jf94qq3d0b` (`booking_id`),
  KEY `idx_review_spa` (`spa_id`,`created_at` DESC),
  KEY `idx_review_user` (`user_id`,`created_at` DESC),
  CONSTRAINT `FKc7y0l3wac4n2ewm6a2uecd54c` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `FKhjj6op1hhl77hmtjhn8401ewo` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKk4xawqohtguy5yx5nnpba6yf3` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_type` enum('ADMIN','CUSTOMER','Spa_MANAGER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8nhufvk7ufr23s4xoqglqtbdx` (`role_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schedule_rule`
--

DROP TABLE IF EXISTS `schedule_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applies_from` date DEFAULT NULL,
  `applies_to` date DEFAULT NULL,
  `close_local` time(6) NOT NULL,
  `is_holiday` bit(1) NOT NULL,
  `meta` json DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `open_local` time(6) NOT NULL,
  `version` bigint DEFAULT NULL,
  `weekday` smallint NOT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_spa_day` (`spa_id`,`weekday`),
  KEY `idx_schedule_spa_applies` (`spa_id`,`applies_from`,`applies_to`,`weekday`),
  CONSTRAINT `FKdldprtwd2km7qj06xhp81svmo` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `service`
--

DROP TABLE IF EXISTS `service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `base_price_cents` int NOT NULL,
  `buffer_min` int NOT NULL,
  `cancellation_policy_json` json DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `currency` varchar(255) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `duration_min` int NOT NULL,
  `duration_minutes` int DEFAULT NULL,
  `equipment_required` json DEFAULT NULL,
  `gender_allowed` enum('ANY','FEMALE','MALE') NOT NULL,
  `images` json DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `is_visible_on_marketplace` bit(1) NOT NULL,
  `max_persons` int NOT NULL,
  `meta` json DEFAULT NULL,
  `min_persons` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `price_breakdown` json DEFAULT NULL,
  `price_cents` int DEFAULT NULL,
  `service_code` varchar(255) DEFAULT NULL,
  `sub_category` varchar(255) DEFAULT NULL,
  `therapist_gender_preference` varchar(255) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK75p8oeffx30y1dh1xow0htsyt` (`service_code`),
  KEY `idx_service_spa` (`spa_id`),
  CONSTRAINT `FKqj4ntqj8ctxar0jgryqnvd7ys` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `slot`
--

DROP TABLE IF EXISTS `slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `slot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `booked_units` smallint NOT NULL,
  `capacity_unit` smallint NOT NULL,
  `end_ts` datetime(6) NOT NULL,
  `hold_expires_ts` datetime(6) DEFAULT NULL,
  `hold_token` varchar(255) DEFAULT NULL,
  `is_blocked` bit(1) NOT NULL,
  `meta` json DEFAULT NULL,
  `room_number` varchar(255) DEFAULT NULL,
  `start_ts` datetime(6) NOT NULL,
  `status` enum('BOOKED','HELD','OPEN') NOT NULL,
  `version` bigint DEFAULT NULL,
  `assigned_provider_user_id` bigint DEFAULT NULL,
  `service_id` bigint NOT NULL,
  `spa_id` bigint NOT NULL,
  `room_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK46kvwxpli03ripcoe5ljfn0v` (`assigned_provider_user_id`),
  KEY `FK3tyrkhpneslq6u3lehpkwjtoj` (`service_id`),
  KEY `FKs3x2sy2n21v1xbef46wnd7h90` (`spa_id`),
  KEY `FKtfjrlp8jp6567stes0n8s63mq` (`room_id`),
  CONSTRAINT `FK3tyrkhpneslq6u3lehpkwjtoj` FOREIGN KEY (`service_id`) REFERENCES `service` (`id`),
  CONSTRAINT `FK46kvwxpli03ripcoe5ljfn0v` FOREIGN KEY (`assigned_provider_user_id`) REFERENCES `provider_user` (`id`),
  CONSTRAINT `FKs3x2sy2n21v1xbef46wnd7h90` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKtfjrlp8jp6567stes0n8s63mq` FOREIGN KEY (`room_id`) REFERENCES `spa_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `spa`
--

DROP TABLE IF EXISTS `spa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address_line1` varchar(255) NOT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `address_type` varchar(50) DEFAULT NULL,
  `city` varchar(100) NOT NULL,
  `country` varchar(100) DEFAULT NULL,
  `country_code` varchar(4) DEFAULT NULL,
  `formatted_address` varchar(500) DEFAULT NULL,
  `geo_source` varchar(50) DEFAULT NULL,
  `google_place_id` varchar(255) DEFAULT NULL,
  `landmark` varchar(255) DEFAULT NULL,
  `latitude` decimal(9,6) DEFAULT NULL,
  `locality` varchar(255) DEFAULT NULL,
  `longitude` decimal(9,6) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `postal_code` varchar(20) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `timezone` varchar(50) DEFAULT NULL,
  `amenities` json DEFAULT NULL,
  `business_reg_number` varchar(255) DEFAULT NULL,
  `commission_pct` int NOT NULL,
  `default_currency` varchar(255) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `established_at` datetime(6) DEFAULT NULL,
  `facebook_url` varchar(255) DEFAULT NULL,
  `gstin` varchar(255) DEFAULT NULL,
  `images` json DEFAULT NULL,
  `instagram_url` varchar(255) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `is_featured` bit(1) NOT NULL,
  `is_verified` bit(1) NOT NULL,
  `kyc_approved_at` datetime(6) DEFAULT NULL,
  `kyc_rejected_at` datetime(6) DEFAULT NULL,
  `kyc_rejected_reason` varchar(1000) DEFAULT NULL,
  `kyc_requested_at` datetime(6) DEFAULT NULL,
  `kyc_status` enum('PENDING','REJECTED','VERIFIED') NOT NULL,
  `max_advance_booking_days` int DEFAULT NULL,
  `max_concurrent_services` int DEFAULT NULL,
  `min_notice_minutes` int DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `owner_name` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `rating_avg` float DEFAULT NULL,
  `rating_count` int DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `tax_percent` int NOT NULL,
  `total_bookings` bigint DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `website_url` varchar(255) DEFAULT NULL,
  `spa_manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKeq88riwmmjvqmi1pebsqtsq6b` (`spa_manager_id`),
  KEY `idx_spa_city_state` (`city`,`state`),
  KEY `idx_spa_active` (`is_active`),
  KEY `idx_spa_name` (`name`),
  CONSTRAINT `FK3dcb9k2ceh86r5m5iq5v299jk` FOREIGN KEY (`spa_manager_id`) REFERENCES `spa_manager` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `spa_manager`
--

DROP TABLE IF EXISTS `spa_manager`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spa_manager` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkh19ulm2dmaaaxhqw3o4jolb2` (`user_id`),
  CONSTRAINT `FKrua7k29y4xm4ee51v5ni1fj6w` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `spa_room`
--

DROP TABLE IF EXISTS `spa_room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `spa_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `capacity` int DEFAULT NULL,
  `code` varchar(64) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_room_spa` (`spa_id`,`name`),
  CONSTRAINT `FKs27a6dq9rs5oyiai94tt962h4` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `therapist`
--

DROP TABLE IF EXISTS `therapist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `therapist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `bio` varchar(2000) DEFAULT NULL,
  `certifications` json DEFAULT NULL,
  `country_of_origin` varchar(128) DEFAULT NULL,
  `currency` varchar(8) DEFAULT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `ethnicity` varchar(128) DEFAULT NULL,
  `experience_years` int DEFAULT NULL,
  `gender` enum('FEMALE','MALE','NON_BINARY','OTHER','UNSPECIFIED') DEFAULT NULL,
  `hourly_rate_cents` int DEFAULT NULL,
  `images` json DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `is_available` bit(1) NOT NULL,
  `languages` json DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `meta` json DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `profile_image_url` varchar(1000) DEFAULT NULL,
  `rating_avg` float DEFAULT NULL,
  `rating_count` int DEFAULT NULL,
  `reviews` json DEFAULT NULL,
  `type` varchar(128) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  `spa_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_therapist_spa` (`spa_id`),
  KEY `idx_therapist_name` (`name`),
  KEY `FKq2gsvg8wxwrqx9h6bsthjq0sq` (`service_id`),
  CONSTRAINT `FK4hhlc10t1oxq6k2u3wgod3jcd` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKq2gsvg8wxwrqx9h6bsthjq0sq` FOREIGN KEY (`service_id`) REFERENCES `service` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `therapist_service`
--

DROP TABLE IF EXISTS `therapist_service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `therapist_service` (
  `therapist_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  PRIMARY KEY (`therapist_id`,`service_id`),
  KEY `idx_therapist_service_th` (`therapist_id`),
  KEY `idx_therapist_service_sv` (`service_id`),
  CONSTRAINT `FKfjsnx2fqamnhkbhi0eapsh7dy` FOREIGN KEY (`service_id`) REFERENCES `service` (`id`),
  CONSTRAINT `FKl2erm51nkde7jtbxouqrjeo92` FOREIGN KEY (`therapist_id`) REFERENCES `therapist` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_favorite_spa`
--

DROP TABLE IF EXISTS `user_favorite_spa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorite_spa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint DEFAULT NULL,
  `spa_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_spa_fav` (`user_id`,`spa_id`),
  KEY `FKe58c4n06k0oa1hf5cs8hpo0fv` (`spa_id`),
  CONSTRAINT `FKe58c4n06k0oa1hf5cs8hpo0fv` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`),
  CONSTRAINT `FKf721f6gsa9fpxjo6q9a6b0yfx` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_favorite_therapist`
--

DROP TABLE IF EXISTS `user_favorite_therapist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorite_therapist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint DEFAULT NULL,
  `therapist_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_therapist_fav` (`user_id`,`therapist_id`),
  KEY `FKgacclqqbgfcw60vttk6gbilhm` (`therapist_id`),
  CONSTRAINT `FKgacclqqbgfcw60vttk6gbilhm` FOREIGN KEY (`therapist_id`) REFERENCES `therapist` (`id`),
  CONSTRAINT `FKo1pa4sylm9xuswhb90e6m7bt5` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_membership`
--

DROP TABLE IF EXISTS `user_membership`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_membership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `meta` json DEFAULT NULL,
  `start_at` datetime(6) NOT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED') NOT NULL,
  `version` bigint DEFAULT NULL,
  `plan_id` bigint NOT NULL,
  `spa_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_membership_user` (`user_id`,`status`),
  KEY `idx_user_membership_spa` (`spa_id`,`status`),
  KEY `FKlxyh1xwemhfcw179isahum34x` (`plan_id`),
  CONSTRAINT `FKglr12nwnav6ettabyobo0k63v` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`),
  CONSTRAINT `FKlxyh1xwemhfcw179isahum34x` FOREIGN KEY (`plan_id`) REFERENCES `membership_plan` (`id`),
  CONSTRAINT `FKrssvl9lnpmn61xr0l0qdu0uj0` FOREIGN KEY (`spa_id`) REFERENCES `spa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_roles`
--

DROP TABLE IF EXISTS `user_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  KEY `FK6fql8djp64yp4q9b3qeyhr82b` (`user_id`),
  CONSTRAINT `FK6fql8djp64yp4q9b3qeyhr82b` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-23  2:23:21
