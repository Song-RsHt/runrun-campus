BEGIN;
ALTER TABLE runrun_v2.courier_certifications ADD COLUMN approved_photo_at TIMESTAMPTZ;
CREATE TABLE runrun_v2.evidence_holds (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 certification_id BIGINT REFERENCES runrun_v2.courier_certifications(id),
 order_id BIGINT,
 reason TEXT NOT NULL,
 owner_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
 review_due_at TIMESTAMPTZ NOT NULL,
 released_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((certification_id IS NULL) <> (order_id IS NULL))
);
CREATE INDEX evidence_holds_cert_idx ON runrun_v2.evidence_holds(certification_id,released_at);
CREATE TABLE runrun_v2.evidence_deletion_audits (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 certification_id BIGINT REFERENCES runrun_v2.courier_certifications(id),
 resource_type TEXT NOT NULL,
 resource_id TEXT NOT NULL,
 resource_storage_key TEXT,
 delete_after TIMESTAMPTZ NOT NULL,
 policy_snapshot JSONB NOT NULL,
 status TEXT NOT NULL CHECK (status IN ('REQUESTED','DELETING','SUCCEEDED','FAILED')),
 attempt_no INTEGER NOT NULL DEFAULT 0,
 finished_at TIMESTAMPTZ,
 error_code TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX evidence_deletion_due_idx ON runrun_v2.evidence_deletion_audits(status,delete_after);
COMMIT;
