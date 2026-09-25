ALTER TABLE medical_documents ADD COLUMN storage_key VARCHAR(500);
ALTER TABLE medical_documents ADD COLUMN original_file_name VARCHAR(255);
ALTER TABLE medical_documents ADD COLUMN content_type VARCHAR(120);
ALTER TABLE medical_documents ADD COLUMN file_size BIGINT;
CREATE UNIQUE INDEX uq_medical_documents_storage_key ON medical_documents(storage_key) WHERE storage_key IS NOT NULL;
