BEGIN;
DROP TABLE IF EXISTS runrun_v2.evidence_deletion_audits;
DROP TABLE IF EXISTS runrun_v2.evidence_holds;
ALTER TABLE runrun_v2.courier_certifications DROP COLUMN IF EXISTS approved_photo_at;
ALTER TABLE runrun_v2.courier_certifications DROP COLUMN IF EXISTS approved_photo_resource_id;
ALTER TABLE runrun_v2.courier_certifications DROP COLUMN IF EXISTS submitted_photo_resource_id;
ALTER TABLE runrun_v2.courier_certifications DROP COLUMN IF EXISTS version;
COMMIT;
