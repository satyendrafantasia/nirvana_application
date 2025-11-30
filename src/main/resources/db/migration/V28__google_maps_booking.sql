-- Add Google Maps linkage metadata to spas and capture booking source

ALTER TABLE spa
    ADD COLUMN google_maps_url varchar(500) NULL AFTER google_place_id;

CREATE UNIQUE INDEX idx_spa_google_place_id ON spa (google_place_id);

ALTER TABLE booking
    ADD COLUMN booking_source enum('APP','WEB','GOOGLE_MAPS','INSTAGRAM','API','OTHER') NOT NULL DEFAULT 'WEB' AFTER status;
