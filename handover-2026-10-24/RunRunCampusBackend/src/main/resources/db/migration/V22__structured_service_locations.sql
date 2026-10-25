ALTER TABLE campus_orders ADD COLUMN pickup_station VARCHAR(32);
ALTER TABLE campus_orders ADD COLUMN delivery_zone VARCHAR(16);
ALTER TABLE user_addresses ADD COLUMN delivery_zone VARCHAR(16);

UPDATE campus_orders
SET pickup_station = CASE
    WHEN service_type LIKE 'PACKAGE_%' THEN 'CAMPUS_PACKAGE_STATION'
    ELSE 'EAST_GATE_TAKEOUT'
END,
delivery_zone = CASE WHEN destination LIKE '%梅园%' THEN 'MEIYUAN' ELSE 'STANDARD' END;

UPDATE user_addresses
SET delivery_zone = CASE WHEN address LIKE '%梅园%' THEN 'MEIYUAN' ELSE 'STANDARD' END;

ALTER TABLE campus_orders ALTER COLUMN pickup_station SET NOT NULL;
ALTER TABLE campus_orders ALTER COLUMN delivery_zone SET NOT NULL;
ALTER TABLE user_addresses ALTER COLUMN delivery_zone SET NOT NULL;
