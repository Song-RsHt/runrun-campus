ALTER TABLE courier_certifications
    ADD COLUMN requested_valid_until DATE,
    ADD COLUMN approved_valid_until DATE,
    ADD COLUMN submitted_photo_key VARCHAR(160),
    ADD COLUMN submitted_photo_content_type VARCHAR(32),
    ADD COLUMN submitted_photo_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN approved_photo_key VARCHAR(160),
    ADD COLUMN approved_photo_content_type VARCHAR(32),
    ADD COLUMN approval_retained BOOLEAN NOT NULL DEFAULT false;
