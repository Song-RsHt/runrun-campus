ALTER TABLE app_users
    ADD COLUMN privacy_policy_version VARCHAR(32),
    ADD COLUMN privacy_consent_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE app_users
    ADD CONSTRAINT app_users_privacy_consent_pair_check CHECK (
        (privacy_policy_version IS NULL AND privacy_consent_at IS NULL)
        OR (privacy_policy_version IS NOT NULL AND privacy_consent_at IS NOT NULL)
    );

CREATE INDEX idx_app_users_deleted_at ON app_users (deleted_at) WHERE deleted_at IS NOT NULL;
