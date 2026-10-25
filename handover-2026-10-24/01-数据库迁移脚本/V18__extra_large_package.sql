ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_service_type_check;

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_service_type_check CHECK (
    service_type IN (
        'LOBBY', 'DOOR', 'EXPRESS',
        'PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE', 'PACKAGE_XL'
    )
);

ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_proposed_package_service_type_check;

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_proposed_package_service_type_check CHECK (
    proposed_package_service_type IS NULL
    OR (
        service_type IN ('PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE', 'PACKAGE_XL')
        AND proposed_package_service_type IN ('PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE', 'PACKAGE_XL')
    )
);

ALTER TABLE order_proofs DROP CONSTRAINT order_proofs_type_check;

ALTER TABLE order_proofs ADD CONSTRAINT order_proofs_type_check CHECK (
    type IN ('SIZE_REVIEW', 'PICKUP', 'DELIVERY')
);
