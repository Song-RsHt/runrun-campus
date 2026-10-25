ALTER TABLE campus_orders
    ADD COLUMN payment_method VARCHAR(24) NOT NULL DEFAULT 'WECHAT_APP';

ALTER TABLE campus_orders
    ADD CONSTRAINT campus_orders_payment_method_check
        CHECK (payment_method IN ('WECHAT_APP', 'CASH_ON_DELIVERY'));
