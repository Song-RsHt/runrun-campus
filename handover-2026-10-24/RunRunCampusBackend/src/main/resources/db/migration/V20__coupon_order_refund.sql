ALTER TABLE campus_orders
    ADD COLUMN coupon_id BIGINT REFERENCES user_coupons(id);

UPDATE campus_orders orders
SET coupon_id = (
    SELECT coupons.id
    FROM user_coupons coupons
    WHERE coupons.user_id = orders.customer_id
      AND coupons.title = orders.coupon_title
      AND coupons.used_at <= orders.created_at
    ORDER BY coupons.used_at DESC
    LIMIT 1
)
WHERE orders.coupon_title IS NOT NULL
  AND COALESCE(orders.membership_used, FALSE) = FALSE;

UPDATE user_coupons coupons
SET used_at = NULL
WHERE coupons.id IN (
    SELECT orders.coupon_id
    FROM campus_orders orders
    WHERE orders.status = 'CANCELLED'
      AND orders.coupon_id IS NOT NULL
);
