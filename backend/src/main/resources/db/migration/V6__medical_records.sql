CREATE TABLE medical_events (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    event_date DATE NOT NULL,
    title VARCHAR(200) NOT NULL,
    diagnosis VARCHAR(255),
    provider VARCHAR(160),
    notes TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_medical_event_type CHECK (event_type IN ('VET_VISIT','DIAGNOSIS','LAB_RESULT','OTHER'))
);
CREATE INDEX idx_medical_events_animal_date ON medical_events(animal_id,event_date DESC);

CREATE TABLE vaccinations (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    vaccine_name VARCHAR(160) NOT NULL,
    vaccine_type VARCHAR(100),
    administered_date DATE NOT NULL,
    next_due_date DATE,
    batch_number VARCHAR(100),
    veterinarian VARCHAR(160),
    notes TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_vaccinations_animal_date ON vaccinations(animal_id,administered_date DESC);
CREATE INDEX idx_vaccinations_due ON vaccinations(next_due_date);

CREATE TABLE dewormings (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    product_name VARCHAR(160) NOT NULL,
    treatment_type VARCHAR(100),
    administered_date DATE NOT NULL,
    next_due_date DATE,
    dose VARCHAR(100),
    veterinarian VARCHAR(160),
    notes TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dewormings_animal_date ON dewormings(animal_id,administered_date DESC);
CREATE INDEX idx_dewormings_due ON dewormings(next_due_date);

CREATE TABLE medical_documents (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    document_date DATE,
    notes TEXT,
    uploaded_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_medical_documents_animal ON medical_documents(animal_id);
