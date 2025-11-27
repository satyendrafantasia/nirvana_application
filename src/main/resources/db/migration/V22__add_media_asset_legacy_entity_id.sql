-- Add legacy entity_id alias for media_asset to maintain backward compatibility with older mappings
ALTER TABLE media_asset
    ADD COLUMN IF NOT EXISTS entity_id BIGINT GENERATED ALWAYS AS (spa_id) STORED;
