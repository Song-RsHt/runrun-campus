UPDATE platform_settings
SET max_active_orders = 10, updated_at = now()
WHERE id = 1;
