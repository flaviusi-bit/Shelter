ALTER TABLE animals ADD COLUMN photo_storage_key VARCHAR(1200);
CREATE INDEX idx_animals_photo_storage_key ON animals(photo_storage_key);