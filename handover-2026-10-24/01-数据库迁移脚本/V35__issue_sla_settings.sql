ALTER TABLE platform_settings
    ADD COLUMN issue_pending_minutes INTEGER NOT NULL DEFAULT 30
        CHECK (issue_pending_minutes BETWEEN 1 AND 1440),
    ADD COLUMN issue_processing_minutes INTEGER NOT NULL DEFAULT 120
        CHECK (issue_processing_minutes BETWEEN 1 AND 1440);
