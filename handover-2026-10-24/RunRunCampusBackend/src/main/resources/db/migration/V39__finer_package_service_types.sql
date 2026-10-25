ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_service_type_check;

ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_proposed_package_service_type_check;

ALTER TABLE campus_orders ALTER COLUMN service_type TYPE VARCHAR(32);

ALTER TABLE campus_orders ALTER COLUMN proposed_package_service_type TYPE VARCHAR(32);

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_service_type_check CHECK (
    service_type IN (
        'LOBBY', 'DOOR', 'EXPRESS',
        'PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_MEDIUM_LARGE',
        'PACKAGE_LARGE', 'PACKAGE_EXTRA_LARGE', 'PACKAGE_XL'
    )
);

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_proposed_package_service_type_check CHECK (
    proposed_package_service_type IS NULL
    OR (
        service_type IN (
            'PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_MEDIUM_LARGE',
            'PACKAGE_LARGE', 'PACKAGE_EXTRA_LARGE', 'PACKAGE_XL'
        )
        AND proposed_package_service_type IN (
            'PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_MEDIUM_LARGE',
            'PACKAGE_LARGE', 'PACKAGE_EXTRA_LARGE', 'PACKAGE_XL'
        )
    )
);
