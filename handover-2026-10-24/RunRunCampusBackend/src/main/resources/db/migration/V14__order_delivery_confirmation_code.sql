ALTER TABLE campus_orders ADD COLUMN delivery_code VARCHAR(6);

UPDATE campus_orders
SET delivery_code = LPAD(FLOOR(RANDOM() * 1000000)::INTEGER::TEXT, 6, '0')
WHERE delivery_code IS NULL;

ALTER TABLE campus_orders ALTER COLUMN delivery_code SET NOT NULL;
ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_delivery_code_check
    CHECK (delivery_code ~ '^[0-9]{6}$');
