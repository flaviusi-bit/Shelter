CREATE TABLE treatments (
 id UUID PRIMARY KEY,
 animal_id UUID NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
 medication VARCHAR(160) NOT NULL,
 dose VARCHAR(80) NOT NULL,
 route VARCHAR(40) NOT NULL,
 frequency VARCHAR(80) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE,
 status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
 instructions TEXT,
 prescribed_by VARCHAR(120)
);
CREATE INDEX idx_treatments_animal ON treatments(animal_id);
CREATE INDEX idx_treatments_status ON treatments(status);
