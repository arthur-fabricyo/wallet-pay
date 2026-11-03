ALTER TABLE transactions ALTER COLUMN sender_wallet_id DROP NOT NULL;

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_type;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
    CHECK (type IN ('TRANSFER', 'DEPOSIT'));

ALTER TABLE transactions ADD CONSTRAINT ck_transactions_transfer_has_sender
    CHECK (type <> 'TRANSFER' OR sender_wallet_id IS NOT NULL);

ALTER TABLE transactions ADD CONSTRAINT ck_transactions_deposit_has_no_sender
    CHECK (type <> 'DEPOSIT' OR sender_wallet_id IS NULL);
