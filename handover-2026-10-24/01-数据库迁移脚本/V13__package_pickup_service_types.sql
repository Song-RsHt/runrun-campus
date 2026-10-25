ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_service_type_check;

ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_service_type_check CHECK (
    service_type IN ('LOBBY', 'DOOR', 'EXPRESS', 'PACKAGE_SMALL', 'PACKAGE_MEDIUM', 'PACKAGE_LARGE')
);
