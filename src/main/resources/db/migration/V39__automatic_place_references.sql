CREATE TABLE place_discovery_reference (
    place_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    search_mode VARCHAR(10) NOT NULL,
    search_city VARCHAR(100),
    search_locality VARCHAR(100),
    country_code VARCHAR(2) NOT NULL,
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    search_count BIGINT NOT NULL DEFAULT 1
);
