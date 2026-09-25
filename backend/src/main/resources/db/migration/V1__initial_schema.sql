CREATE TABLE animals (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    animal_type VARCHAR(30) NOT NULL,
    sex VARCHAR(20) NOT NULL,
    date_of_birth DATE,
    weight_kg NUMERIC(7,3),
    microchip_number VARCHAR(80),
    intake_date DATE NOT NULL,
    rescue_source VARCHAR(255),
    location VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_animals_status ON animals(status);
CREATE INDEX idx_animals_microchip ON animals(microchip_number);
CREATE INDEX idx_animals_location ON animals(location);

CREATE TABLE medical_records (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    record_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(120)
);

CREATE INDEX idx_medical_records_animal ON medical_records(animal_id);
