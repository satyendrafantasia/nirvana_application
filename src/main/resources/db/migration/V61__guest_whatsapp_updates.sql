-- When a guest agreed to booking updates on WhatsApp (WhatsApp only allows messages people opted into).
-- NULL means no: they get email only.
ALTER TABLE app_user ADD COLUMN whatsapp_updates_at datetime(6) DEFAULT NULL;
