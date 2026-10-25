ALTER TABLE app_users ADD COLUMN courier_accepting_orders BOOLEAN;
ALTER TABLE app_users ADD COLUMN courier_availability_updated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE app_users
SET courier_accepting_orders = FALSE
WHERE role = 'COURIER';
