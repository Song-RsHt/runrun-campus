WITH orders AS (
    SELECT * FROM campus_orders WHERE customer_id BETWEEN -3039 AND -3000
), events AS (
    SELECT e.* FROM order_events e JOIN orders o ON o.id=e.order_id
), messages AS (
    SELECT m.* FROM order_messages m JOIN orders o ON o.id=m.order_id
), used_coupons AS (
    SELECT * FROM user_coupons WHERE id BETWEEN -102099 AND -100000 AND used_at IS NOT NULL
)
SELECT json_build_object(
    'orders', (SELECT count(*) FROM orders),
    'accepted', (SELECT count(*) FROM orders WHERE status='ACCEPTED'),
    'couponOrders', (SELECT count(*) FROM orders WHERE coupon_title IS NOT NULL),
    'membershipOrders', (SELECT count(*) FROM orders WHERE membership_used),
    'creationEvents', (SELECT count(*) FROM events WHERE status='PENDING_ASSIGNMENT'),
    'acceptEvents', (SELECT count(*) FROM events WHERE status='ACCEPTED'),
    'acceptMessages', (SELECT count(*) FROM messages WHERE type='ORDER_ACCEPTED'),
    'usedCoupons', (SELECT count(*) FROM used_coupons),
    'membershipTripsConsumed', (SELECT sum(total_trips-remaining_trips) FROM user_memberships WHERE id BETWEEN -3039 AND -3000),
    'invalidOrders', (SELECT count(*) FROM orders o WHERE
        request_key IS NULL OR request_fingerprint IS NULL
        OR status NOT IN ('PENDING_ASSIGNMENT','ACCEPTED')
        OR (status='PENDING_ASSIGNMENT' AND courier_id IS NOT NULL)
        OR (status='ACCEPTED' AND (courier_id IS NULL OR courier_id NOT BETWEEN -4039 AND -4000))
        OR service_type <> 'PACKAGE_MEDIUM' OR original_fee_cents <> 200
        OR discount_cents <> CASE WHEN membership_used THEN 200 WHEN coupon_title IS NOT NULL THEN 100 ELSE 0 END
        OR fee_cents <> original_fee_cents-discount_cents
        OR (membership_used AND coupon_title IS NOT NULL)
        OR (SELECT count(*) FROM events e WHERE e.order_id=o.id AND e.status='PENDING_ASSIGNMENT' AND e.actor_id=o.customer_id)<>1
        OR (SELECT count(*) FROM events e WHERE e.order_id=o.id)<>CASE WHEN status='ACCEPTED' THEN 2 ELSE 1 END
        OR (status='ACCEPTED' AND (SELECT count(*) FROM events e WHERE e.order_id=o.id AND e.status='ACCEPTED' AND e.actor_id=o.courier_id)<>1)
        OR (SELECT count(*) FROM messages m WHERE m.order_id=o.id)<>CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END
        OR (status='ACCEPTED' AND (SELECT count(*) FROM messages m WHERE m.order_id=o.id AND m.type='ORDER_ACCEPTED' AND m.recipient_id=o.customer_id)<>1)),
    'duplicateKeys', (SELECT count(*) FROM (SELECT customer_id, request_key FROM orders GROUP BY customer_id, request_key HAVING count(*)<>1) d),
    'invalidCoupons', (SELECT count(*) FROM user_coupons c WHERE id BETWEEN -102099 AND -100000
        AND (SELECT count(*) FROM orders o WHERE o.customer_id=c.user_id AND o.coupon_title=c.title)<>CASE WHEN c.used_at IS NULL THEN 0 ELSE 1 END),
    'invalidMemberships', (SELECT count(*) FROM user_memberships m WHERE id BETWEEN -3039 AND -3000
        AND (remaining_trips<0 OR total_trips-remaining_trips<>(SELECT count(*) FROM orders o WHERE o.customer_id=m.user_id AND o.membership_used)))
);
