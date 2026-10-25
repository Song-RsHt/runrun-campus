ALTER TABLE campus_orders
    ADD COLUMN package_size_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN proposed_package_service_type VARCHAR(16);

UPDATE campus_orders
SET package_size_confirmed = FALSE
WHERE service_type IN ('PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE')
  AND status IN ('PENDING_ASSIGNMENT', 'ACCEPTED');

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_proposed_package_service_type_check CHECK (
    proposed_package_service_type IS NULL
    OR (
        service_type IN ('PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE')
        AND proposed_package_service_type IN ('PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE')
    )
);

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_package_size_confirmation_check CHECK (
    package_size_confirmed = FALSE OR proposed_package_service_type IS NULL
);
