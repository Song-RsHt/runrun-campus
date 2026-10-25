ALTER TABLE campus_orders
    ADD COLUMN membership_id BIGINT REFERENCES user_memberships(id);

UPDATE campus_orders orders
SET membership_id = (
    SELECT memberships.id
    FROM user_memberships memberships
    WHERE memberships.user_id = orders.customer_id
      AND memberships.purchased_at <= orders.created_at
      AND memberships.expires_at > orders.created_at
    ORDER BY memberships.purchased_at DESC
    LIMIT 1
)
WHERE orders.membership_used = TRUE;

UPDATE user_memberships memberships
SET remaining_trips = LEAST(memberships.total_trips,
    memberships.remaining_trips + cancelled_orders.refund_count)
FROM (
    SELECT membership_id, COUNT(*)::INTEGER AS refund_count
    FROM campus_orders
    WHERE membership_used = TRUE
      AND status = 'CANCELLED'
      AND membership_id IS NOT NULL
    GROUP BY membership_id
) cancelled_orders
WHERE memberships.id = cancelled_orders.membership_id;
