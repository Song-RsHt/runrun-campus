ALTER TABLE campus_orders ADD COLUMN request_key UUID;
ALTER TABLE campus_orders ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE campus_orders ADD CONSTRAINT uk_campus_orders_customer_request UNIQUE (customer_id, request_key);

CREATE SEQUENCE order_number_seq START WITH 1;
