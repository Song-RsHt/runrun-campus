BEGIN;
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT count(*) INTO n FROM runrun_v2.evidence_holds;
  IF n > 0 THEN RAISE EXCEPTION 'M06 rollback blocked: evidence_holds has % rows', n; END IF;
  SELECT count(*) INTO n FROM runrun_v2.evidence_deletion_audits;
  IF n > 0 THEN RAISE EXCEPTION 'M06 rollback blocked: deletion audits must be retained'; END IF;
  SELECT count(*) INTO n FROM runrun_v2.courier_certifications
    WHERE submitted_photo_resource_id IS NOT NULL OR approved_photo_resource_id IS NOT NULL;
  IF n > 0 THEN RAISE EXCEPTION 'M06 rollback blocked: live student-card resources exist'; END IF;
  SELECT count(*) INTO n FROM runrun_v2.courier_certification_events;
  IF n > 0 THEN RAISE EXCEPTION 'M06 rollback blocked: certification audit events must be retained'; END IF;
END $$;
-- version and resource columns are owned by the v2 identity foundation (0002).
ALTER TABLE runrun_v2.courier_certifications DROP COLUMN IF EXISTS approved_photo_at;
DROP TABLE runrun_v2.evidence_deletion_audits;
DROP TABLE runrun_v2.evidence_holds;
COMMIT;
