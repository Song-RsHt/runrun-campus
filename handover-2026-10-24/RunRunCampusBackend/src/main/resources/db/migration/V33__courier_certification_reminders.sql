ALTER TABLE push_delivery_tasks
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN destination VARCHAR(40),
    ADD CONSTRAINT push_delivery_tasks_route_check CHECK (
        (order_id IS NOT NULL AND destination IS NULL)
        OR (order_id IS NULL AND destination = 'COURIER_CERTIFICATION')
    );

CREATE INDEX idx_courier_certifications_valid_until
    ON courier_certifications (approved_valid_until, id)
    WHERE approved_valid_until IS NOT NULL;

CREATE INDEX idx_courier_certifications_rejected_cleanup
    ON courier_certifications (reviewed_at, id)
    WHERE status = 'REJECTED' AND submitted_photo_key IS NOT NULL;
