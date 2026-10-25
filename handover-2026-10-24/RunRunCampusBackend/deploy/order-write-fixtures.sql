-- Only invoked after the runner verifies this rehearsal's isolated containers and network.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM app_users WHERE id BETWEEN -3039 AND -3000 OR id BETWEEN -4039 AND -4000)
       OR EXISTS (SELECT 1 FROM user_sessions WHERE token LIKE 'write-probe-%')
       OR EXISTS (SELECT 1 FROM user_sessions WHERE id BETWEEN -3039 AND -3000 OR id BETWEEN -4039 AND -4000)
       OR EXISTS (SELECT 1 FROM user_coupons WHERE id BETWEEN -102099 AND -100000)
       OR EXISTS (SELECT 1 FROM user_memberships WHERE id BETWEEN -3039 AND -3000) THEN
        RAISE EXCEPTION 'Write fixture identifiers already exist';
    END IF;
END $$;
INSERT INTO app_users(id, created_at, display_name, password_hash, phone, role, courier_approved, enabled, failed_login_attempts)
SELECT -3000-i, now(), '写入测试用户-' || i, 'unused-test-hash', 'write-probe-customer-' || i, 'CUSTOMER', false, true, 0
FROM generate_series(0,39) i
UNION ALL
SELECT -4000-i, now(), '写入测试骑手-' || i, 'unused-test-hash', 'write-probe-courier-' || i, 'COURIER', true, true, 0
FROM generate_series(0,39) i;
INSERT INTO user_sessions(id, expires_at, token, user_id)
SELECT id, now()+interval '10 minutes',
       CASE role WHEN 'CUSTOMER' THEN 'write-probe-customer-' || (-id-3000)
                 ELSE 'write-probe-courier-' || (-id-4000) END, id
FROM app_users WHERE id BETWEEN -3039 AND -3000 OR id BETWEEN -4039 AND -4000;
INSERT INTO user_coupons(id, version, user_id, title, discount_cents, minimum_order_cents, created_at, expires_at)
SELECT -100000-i, 0, -3000-(i%40), '写入压测券-' || i, 100, 200, now(), now()+interval '1 day'
FROM generate_series(0,2099) i;
INSERT INTO user_memberships(id, version, user_id, plan, total_trips, remaining_trips, price_cents, purchased_at, expires_at)
SELECT -3000-i, 0, -3000-i, 'LIGHT', 10000, 10000, 1600, now(), now()+interval '1 day'
FROM generate_series(0,39) i;
COMMIT;
