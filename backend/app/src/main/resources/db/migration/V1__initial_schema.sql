
CREATE TABLE payments (
    id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100),
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT ukj4pdqn1207fuhx2gybp1ue799
        UNIQUE (idempotency_key)
);

CREATE TABLE payment_status_history (
    id UUID NOT NULL,
    changed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    payment_id UUID NOT NULL,
    CONSTRAINT payment_status_history_pkey PRIMARY KEY (id),
    CONSTRAINT fkpo8yhsq5qm6p4cflhsfbl5kog
        FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE refunds (
    id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_id UUID NOT NULL,
    CONSTRAINT refunds_pkey PRIMARY KEY (id),
    CONSTRAINT uk_refund_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT fkpt9ic0j1y6xwlej99wnynvnpy
        FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE refund_status_history (
    id UUID NOT NULL,
    changed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    refund_id UUID NOT NULL,
    CONSTRAINT refund_status_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk3bew96t1i3obu62u6rssj07di
        FOREIGN KEY (refund_id) REFERENCES refunds(id)
);

CREATE INDEX idx_payment_history_payment_id
    ON payment_status_history(payment_id);

CREATE INDEX idx_refund_history_refund_id
    ON refund_status_history(refund_id);

CREATE INDEX idx_refunds_payment_id
    ON refunds(payment_id);
