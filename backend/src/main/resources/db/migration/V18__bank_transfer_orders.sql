SET LOCAL lock_timeout = '5s';

-- Pro can also be bought by a plain bank transfer to the shop's own account (VietQR),
-- confirmed by an admin once the money shows up in the bank. payOS orders are unchanged.
ALTER TABLE payment_orders DROP CONSTRAINT payment_orders_provider_check;
ALTER TABLE payment_orders ADD CONSTRAINT payment_orders_provider_check
    CHECK (provider IN ('PAYOS', 'BANK_TRANSFER'));

-- When the buyer pressed "I have transferred": the admin's queue is sorted by it.
ALTER TABLE payment_orders ADD COLUMN buyer_confirmed_at TIMESTAMPTZ;

CREATE INDEX idx_payment_orders_bank_queue ON payment_orders (buyer_confirmed_at, created_at)
    WHERE provider = 'BANK_TRANSFER' AND status IN ('PENDING', 'EXPIRED');
