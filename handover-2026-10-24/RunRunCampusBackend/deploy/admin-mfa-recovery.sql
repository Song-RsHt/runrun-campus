-- Rendered by tools/prepare-admin-mfa-recovery.ps1. Run with psql -v ON_ERROR_STOP=1.
-- This deliberately requires database operator access and targets one SUPER_ADMIN.
BEGIN;
SELECT set_config('runrun.mfa.phone', :'phone', true);
SELECT set_config('runrun.mfa.reason', :'reason', true);
SELECT set_config('runrun.mfa.grant_hash', :'grant_hash', true);
DO $$
DECLARE
    target_id BIGINT;
BEGIN
    SELECT id INTO STRICT target_id FROM app_users
      WHERE phone = current_setting('runrun.mfa.phone') AND role = 'ADMIN'
        AND admin_role = 'SUPER_ADMIN' AND enabled = TRUE FOR UPDATE;
    IF current_setting('runrun.mfa.grant_hash') !~ '^[0-9a-f]{64}$'
       OR length(trim(current_setting('runrun.mfa.reason'))) NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'Invalid recovery input';
    END IF;
    UPDATE app_users SET security_version = security_version + 1,
      failed_login_attempts = 0, login_locked_until = NULL WHERE id = target_id;
    DELETE FROM user_sessions WHERE user_id = target_id;
    INSERT INTO admin_mfa_credentials (user_id, reset_grant_hash, reset_grant_expires_at)
      VALUES (target_id, current_setting('runrun.mfa.grant_hash'), now() + interval '24 hours')
      ON CONFLICT (user_id) DO UPDATE SET
        secret_ciphertext = NULL, last_used_step = -1, recovery_hashes = '',
        reset_grant_hash = EXCLUDED.reset_grant_hash, reset_grant_expires_at = EXCLUDED.reset_grant_expires_at,
        challenge_hash = NULL, challenge_expires_at = NULL, challenge_kind = NULL,
        challenge_attempts = 0, pending_secret_ciphertext = NULL;
    INSERT INTO admin_audit_logs (administrator_id, action, target_type, target_id, summary, created_at)
      VALUES (target_id, 'ADMIN_MFA_RESET', 'ADMIN_ACCOUNT', target_id,
        '服务器运维离线恢复：' || current_setting('runrun.mfa.reason'), now());
END $$;
COMMIT;
