-- 1. Create users table
CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 2. Add nullable ownership column for existing payments
ALTER TABLE payments
ADD COLUMN user_id UUID;

-- 3. Create a dedicated legacy system account
INSERT INTO users (
    id,
    email,
    password,
    role,
    created_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'legacy-system@payflow.internal',
    '!DISABLED_LEGACY_ACCOUNT!',
    'SYSTEM',
    CURRENT_TIMESTAMP
);

-- 4. Assign existing payments to the legacy account
UPDATE payments
SET user_id = '00000000-0000-0000-0000-000000000001'
WHERE user_id IS NULL;

-- 5. Enforce payment ownership
ALTER TABLE payments
ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE payments
ADD CONSTRAINT fk_payments_user
FOREIGN KEY (user_id) REFERENCES users(id);

CREATE INDEX idx_payments_user_id
ON payments(user_id);

-- 6. Replace global payment idempotency constraint
ALTER TABLE payments
DROP CONSTRAINT ukj4pdqn1207fuhx2gybp1ue799;

ALTER TABLE payments
ADD CONSTRAINT uk_payments_user_idempotency
UNIQUE (user_id, idempotency_key);

-- 7. Replace global refund idempotency constraint
ALTER TABLE refunds
DROP CONSTRAINT uk_refund_idempotency_key;

ALTER TABLE refunds
ADD CONSTRAINT uk_refunds_payment_idempotency
UNIQUE (payment_id, idempotency_key);