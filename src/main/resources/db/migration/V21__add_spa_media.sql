-- Migration to align spa media management with direct S3 uploads and expanded spa metadata

-- Media asset table adjustments
ALTER TABLE media_asset
    DROP FOREIGN KEY FKlivdkahhw45bl64a262g6wx42;

DROP INDEX idx_media_entity ON media_asset;

ALTER TABLE media_asset
    ADD COLUMN object_key varchar(1000) NULL AFTER entity_id,
    ADD COLUMN title varchar(255) NULL AFTER object_key,
    DROP COLUMN meta,
    DROP COLUMN is_primary,
    DROP COLUMN entity_type,
    DROP COLUMN version;

UPDATE media_asset SET object_key = url WHERE object_key IS NULL;
ALTER TABLE media_asset DROP COLUMN url;
ALTER TABLE media_asset CHANGE COLUMN entity_id spa_id bigint NOT NULL;
ALTER TABLE media_asset MODIFY object_key varchar(1000) NOT NULL;
ALTER TABLE media_asset ADD CONSTRAINT fk_media_asset_spa FOREIGN KEY (spa_id) REFERENCES spa(id);
CREATE INDEX idx_media_asset_spa ON media_asset (spa_id, media_type, position);

-- Spa open/close timings
ALTER TABLE spa
    ADD COLUMN open_time_local TIME NOT NULL DEFAULT '09:00:00',
    ADD COLUMN close_time_local TIME NOT NULL DEFAULT '21:00:00';

-- Working days
CREATE TABLE IF NOT EXISTS spa_working_day (
    spa_id BIGINT NOT NULL,
    day_of_week VARCHAR(16) NOT NULL,
    PRIMARY KEY (spa_id, day_of_week),
    CONSTRAINT fk_spa_working_day_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);

-- Therapist types available at spa
CREATE TABLE IF NOT EXISTS spa_therapist_type (
    spa_id BIGINT NOT NULL,
    therapist_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (spa_id, therapist_type),
    CONSTRAINT fk_spa_therapist_type_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);
