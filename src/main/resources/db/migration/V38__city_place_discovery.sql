CREATE TABLE city_place_discovery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    place_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_city_place (city, country_code, place_id)
);
