CREATE TABLE wallet_limits (
    wallet_id UUID PRIMARY KEY,
    transfer_per_operation NUMERIC(19, 2),
    transfer_daily NUMERIC(19, 2),
    transfer_monthly NUMERIC(19, 2),
    transfer_night_per_operation NUMERIC(19, 2),
    transfer_night_window NUMERIC(19, 2),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallet_limits_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE
);

CREATE TABLE wallet_limits_aud (
    wallet_id UUID NOT NULL,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    transfer_per_operation NUMERIC(19, 2),
    transfer_daily NUMERIC(19, 2),
    transfer_monthly NUMERIC(19, 2),
    transfer_night_per_operation NUMERIC(19, 2),
    transfer_night_window NUMERIC(19, 2),
    version BIGINT,
    updated_at TIMESTAMPTZ,
    PRIMARY KEY (wallet_id, rev),
    CONSTRAINT fk_wallet_limits_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE INDEX idx_transactions_sender_created ON transactions (sender_wallet_id, created_at);
CREATE INDEX idx_transactions_receiver_created ON transactions (receiver_wallet_id, created_at);
