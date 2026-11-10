-- M01 runtime additions. Never query or import public accounts.
BEGIN;
ALTER TABLE runrun_v2.app_users ADD COLUMN controlled_identity BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE runrun_v2.app_users ADD CONSTRAINT app_users_controlled_ck CHECK (NOT controlled_identity OR (role='ADMIN' AND NOT v2_admin_enabled));
ALTER TABLE runrun_v2.app_users ADD CONSTRAINT app_users_admin_enabled_ck CHECK (NOT v2_admin_enabled OR role='ADMIN');
CREATE TABLE runrun_v2.identity_tickets (
  token_hash TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind = 'WECHAT_LOGIN'),
  app_id TEXT,
  openid_hash TEXT,
  openid_ciphertext TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (app_id IS NOT NULL AND openid_hash IS NOT NULL AND openid_ciphertext IS NOT NULL)
);
CREATE INDEX identity_tickets_expiry_idx ON runrun_v2.identity_tickets(expires_at);
CREATE TABLE runrun_v2.identity_audit_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
  event_type TEXT NOT NULL CHECK (event_type IN ('WECHAT_BOUND','SESSION_REVOKED')),
  subject_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX identity_audit_actor_idx ON runrun_v2.identity_audit_events(actor_id);
CREATE INDEX identity_audit_subject_idx ON runrun_v2.identity_audit_events(subject_id,created_at,id);
-- The deployer remains an actual ADMIN FK subject, with no interactive login.
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','V2_ADMIN_CREATED','V2_DEPLOYER_CREATED',
 'COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED'));
COMMIT;
