ALTER TABLE animals ADD COLUMN animal_code VARCHAR(40);
ALTER TABLE animals ADD COLUMN photo_url VARCHAR(1000);

UPDATE animals
SET animal_code = 'A-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 8))
WHERE animal_code IS NULL;

ALTER TABLE animals ALTER COLUMN animal_code SET NOT NULL;
ALTER TABLE animals ADD CONSTRAINT uq_animals_animal_code UNIQUE (animal_code);
CREATE INDEX idx_animals_code ON animals(animal_code);
