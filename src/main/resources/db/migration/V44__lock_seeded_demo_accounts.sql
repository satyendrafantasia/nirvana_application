-- V18 seeded demo accounts (including an administrator) with a plain-text password.
-- Replace every plain-text password with a value no encoder can match, so these
-- accounts cannot be used to sign in with a password in any environment.
UPDATE app_user
SET password = '{locked}'
WHERE password LIKE '{noop}%';
