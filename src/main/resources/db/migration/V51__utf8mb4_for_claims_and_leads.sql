-- These tables were created with the database's default character set, which on some servers is
-- latin1 and rejects names in other scripts (for example a spa named "Spa Serenity スパ・セレニティ").
-- Match the core tables, which use utf8mb4.
ALTER TABLE spa_lead CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE spa_listing_claim CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE listing_claim_phone_check CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
