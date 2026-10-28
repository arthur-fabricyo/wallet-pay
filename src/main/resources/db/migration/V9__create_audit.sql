CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID,
    outcome VARCHAR(10) NOT NULL,
    failure_code VARCHAR(60),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip VARCHAR(45),
    request_id VARCHAR(64),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_audit_events_failure_code CHECK (outcome = 'FAILURE' OR failure_code IS NULL)
);

CREATE INDEX idx_audit_events_actor_occurred ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_events_request_id ON audit_events (request_id);

CREATE FUNCTION prevent_audit_events_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events e append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_events_change();

CREATE TABLE revinfo (
    id BIGSERIAL PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    user_id UUID,
    request_id VARCHAR(64)
);

CREATE TABLE users_aud (
    id UUID NOT NULL,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    name VARCHAR(120),
    email VARCHAR(254),
    document VARCHAR(14),
    email_verified BOOLEAN,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT pk_users_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_users_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE TABLE wallets_aud (
    id UUID NOT NULL,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    user_id UUID,
    balance NUMERIC(19, 2),
    currency VARCHAR(3),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    version BIGINT,
    CONSTRAINT pk_wallets_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_wallets_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE TABLE transactions_aud (
    id UUID NOT NULL,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    sender_wallet_id UUID,
    receiver_wallet_id UUID,
    type VARCHAR(20),
    status VARCHAR(20),
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    description VARCHAR(255),
    created_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_reason VARCHAR(255),
    CONSTRAINT pk_transactions_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_transactions_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (id)
);
