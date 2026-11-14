-- When the password was last changed or reset. Access tokens issued before this moment are
-- refused, so changing a password signs out every other device.
ALTER TABLE app_user ADD COLUMN credentials_changed_at datetime(6) DEFAULT NULL;
