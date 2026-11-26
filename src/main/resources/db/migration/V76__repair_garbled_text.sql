-- One-off repair of text stored garbled: UTF-8 that was read as Windows-1252 and stored again
-- (for example "Juniper Baths Â· Indiranagar" for "Juniper Baths · Indiranagar"). It came from seed
-- scripts that ran the MariaDB client without --default-character-set=utf8mb4.
--
-- A value is repaired only when it has non-ASCII characters, all of them fit one byte each in
-- Windows-1252 (so nothing is lost), and those bytes form well-formed UTF-8, checked before converting.
-- Clean text such as "Café", "gâteau", "Âme Spa" or Hindi fails one of these and is left alone, and no
-- row can make the migration fail. Each pass peels one layer; three passes cover text garbled three times.

UPDATE `spa` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `formatted_address` = CONVERT(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`formatted_address`) < LENGTH(`formatted_address`)
  AND `formatted_address` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `address_line1` = CONVERT(CAST(CONVERT(`address_line1` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`address_line1`) < LENGTH(`address_line1`)
  AND `address_line1` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`address_line1` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `locality` = CONVERT(CAST(CONVERT(`locality` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`locality`) < LENGTH(`locality`)
  AND `locality` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`locality` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `media_asset` SET `title` = CONVERT(CAST(CONVERT(`title` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`title`) < LENGTH(`title`)
  AND `title` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`title` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `display_name` = CONVERT(CAST(CONVERT(`display_name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`display_name`) < LENGTH(`display_name`)
  AND `display_name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`display_name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `bio` = CONVERT(CAST(CONVERT(`bio` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`bio`) < LENGTH(`bio`)
  AND `bio` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`bio` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `formatted_address` = CONVERT(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`formatted_address`) < LENGTH(`formatted_address`)
  AND `formatted_address` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `address_line1` = CONVERT(CAST(CONVERT(`address_line1` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`address_line1`) < LENGTH(`address_line1`)
  AND `address_line1` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`address_line1` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `locality` = CONVERT(CAST(CONVERT(`locality` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`locality`) < LENGTH(`locality`)
  AND `locality` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`locality` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `media_asset` SET `title` = CONVERT(CAST(CONVERT(`title` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`title`) < LENGTH(`title`)
  AND `title` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`title` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `display_name` = CONVERT(CAST(CONVERT(`display_name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`display_name`) < LENGTH(`display_name`)
  AND `display_name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`display_name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `bio` = CONVERT(CAST(CONVERT(`bio` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`bio`) < LENGTH(`bio`)
  AND `bio` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`bio` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `formatted_address` = CONVERT(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`formatted_address`) < LENGTH(`formatted_address`)
  AND `formatted_address` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`formatted_address` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `address_line1` = CONVERT(CAST(CONVERT(`address_line1` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`address_line1`) < LENGTH(`address_line1`)
  AND `address_line1` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`address_line1` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `spa` SET `locality` = CONVERT(CAST(CONVERT(`locality` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`locality`) < LENGTH(`locality`)
  AND `locality` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`locality` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `media_asset` SET `title` = CONVERT(CAST(CONVERT(`title` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`title`) < LENGTH(`title`)
  AND `title` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`title` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `service` SET `description` = CONVERT(CAST(CONVERT(`description` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`description`) < LENGTH(`description`)
  AND `description` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`description` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `name` = CONVERT(CAST(CONVERT(`name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`name`) < LENGTH(`name`)
  AND `name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `display_name` = CONVERT(CAST(CONVERT(`display_name` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`display_name`) < LENGTH(`display_name`)
  AND `display_name` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`display_name` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';

UPDATE `therapist` SET `bio` = CONVERT(CAST(CONVERT(`bio` USING latin1) AS BINARY) USING utf8mb4)
WHERE CHAR_LENGTH(`bio`) < LENGTH(`bio`)
  AND `bio` NOT REGEXP '[^\\x{0000}-\\x{007F}\\x{00A0}-\\x{00FF}\\x{0081}\\x{008D}\\x{008F}\\x{0090}\\x{009D}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]'
  AND HEX(CAST(CONVERT(`bio` USING latin1) AS BINARY)) REGEXP '^([0-7][0-9A-F]|[CD][2-9A-F][89AB][0-9A-F]|E0[AB][0-9A-F][89AB][0-9A-F]|E[1-9A-CEF][89AB][0-9A-F][89AB][0-9A-F]|ED[89][0-9A-F][89AB][0-9A-F]|F0[9AB][0-9A-F]([89AB][0-9A-F]){2}|F[1-3][89AB][0-9A-F]([89AB][0-9A-F]){2}|F48[0-9A-F]([89AB][0-9A-F]){2})*$';
