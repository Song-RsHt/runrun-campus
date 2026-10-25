ALTER TABLE order_chat_messages
    ADD COLUMN read_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE order_chat_messages
SET read_at = created_at;

CREATE INDEX idx_order_chat_recipient_unread
    ON order_chat_messages (recipient_id, order_id)
    WHERE read_at IS NULL;
