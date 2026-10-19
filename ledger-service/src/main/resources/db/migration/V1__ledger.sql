-- ledger-service schema: double-entry bookkeeping, the debits of every transaction equal its credits.

-- One wallet per user plus the company accounts. Assets grow with DEBIT, liabilities with CREDIT.
CREATE TABLE accounts (
    id           UUID PRIMARY KEY,
    owner_id     UUID,
    type         VARCHAR(20)    NOT NULL CHECK (type IN ('PERSONAL_WALLET', 'MERCHANT_WALLET',
                                                       'OPERATING_BANK', 'PARTNER_RESERVE', 'OUTGOING_PENDING')),
    display_name VARCHAR(100),
    currency     VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    balance      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status       VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN')),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- No overdraft, even if the application had a bug.
    CONSTRAINT chk_wallet_balance_not_negative
        CHECK (type NOT IN ('PERSONAL_WALLET', 'MERCHANT_WALLET') OR balance >= 0),
    CONSTRAINT chk_owner_only_on_wallets
        CHECK ((type IN ('PERSONAL_WALLET', 'MERCHANT_WALLET')) = (owner_id IS NOT NULL))
);
-- One wallet per user, so wallet creation is idempotent (ON CONFLICT DO NOTHING).
CREATE UNIQUE INDEX ux_accounts_owner ON accounts (owner_id) WHERE owner_id IS NOT NULL;

-- Company accounts with fixed ids.
INSERT INTO accounts (id, owner_id, type, display_name) VALUES
    ('00000000-0000-7000-9000-000000000001', NULL, 'OPERATING_BANK',   'Operating bank account'),
    ('00000000-0000-7000-9000-000000000002', NULL, 'PARTNER_RESERVE',  'Partner bank reserve'),
    ('00000000-0000-7000-9000-000000000003', NULL, 'OUTGOING_PENDING', 'Withdrawals in progress');

-- One row per business event that moves money.
CREATE TABLE ledger_transactions (
    id                      UUID PRIMARY KEY,
    type                    VARCHAR(24)    NOT NULL CHECK (type IN ('DEPOSIT', 'PAYMENT', 'WITHDRAWAL_HOLD',
                                       'WITHDRAWAL_SETTLED', 'WITHDRAWAL_RELEASED', 'SWEEP', 'REVERSAL')),
    status                  VARCHAR(16)    NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    payment_type            VARCHAR(16)    CHECK (payment_type IN ('P2P', 'MERCHANT')),
    initiated_by            UUID,
    idempotency_key         VARCHAR(100),
    external_reference      VARCHAR(100),
    -- A transaction can be reversed only once.
    reversal_of             UUID UNIQUE REFERENCES ledger_transactions (id),
    payer_account_id        UUID REFERENCES accounts (id),
    payee_account_id        UUID REFERENCES accounts (id),
    amount                  NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency                VARCHAR(3)     NOT NULL,
    resulting_payer_balance NUMERIC(19, 2),
    description             VARCHAR(200),
    processing_ms           INT,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);
-- One transaction per user and Idempotency-Key.
CREATE UNIQUE INDEX ux_tx_idempotency ON ledger_transactions (initiated_by, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
-- One posting per outside fact, so a repeated bank notification is booked only once.
CREATE UNIQUE INDEX ux_tx_external_reference ON ledger_transactions (type, external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX ix_tx_created ON ledger_transactions (created_at DESC);

-- Double-entry lines.
CREATE TABLE ledger_entries (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES ledger_transactions (id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_entries_account ON ledger_entries (account_id, id DESC);
CREATE INDEX ix_entries_transaction ON ledger_entries (transaction_id);

-- Append-only: entries can never be updated or deleted (mistakes are corrected with a REVERSAL).
CREATE OR REPLACE FUNCTION forbid_ledger_entry_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only (attempted %)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_entry_change();

-- Checked at COMMIT (deferred): the debits of each transaction must equal its credits.
CREATE OR REPLACE FUNCTION check_transaction_balanced() RETURNS trigger AS $$
DECLARE
    total_debit  NUMERIC(19, 2);
    total_credit NUMERIC(19, 2);
BEGIN
    SELECT COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0),
           COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0)
      INTO total_debit, total_credit
      FROM ledger_entries
     WHERE transaction_id = NEW.transaction_id;
    IF total_debit <> total_credit THEN
        RAISE EXCEPTION 'Unbalanced ledger transaction %: debits % credits %',
            NEW.transaction_id, total_debit, total_credit;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transaction_balanced();

-- Bank transfers on their way (the pending amounts of the balance).
CREATE TABLE pending_transfers (
    transfer_id       UUID PRIMARY KEY,
    wallet_account_id UUID           NOT NULL REFERENCES accounts (id),
    direction         VARCHAR(8)     NOT NULL CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status            VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    estimated_arrival DATE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_pending_wallet_status ON pending_transfers (wallet_account_id, status);

-- End-of-day liquidity sweeps.
CREATE TABLE sweep_runs (
    id                    UUID PRIMARY KEY,
    amount                NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    reserve_limit         NUMERIC(19, 2) NOT NULL,
    status                VARCHAR(16)    NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    ledger_transaction_id UUID REFERENCES ledger_transactions (id),
    failure_reason        VARCHAR(300),
    requested_by          UUID,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Reconciliation results.
CREATE TABLE reconciliation_runs (
    id                   UUID PRIMARY KEY,
    status               VARCHAR(16)    NOT NULL CHECK (status IN ('RECONCILED', 'MISMATCHED')),
    wallets_total        NUMERIC(19, 2) NOT NULL,
    ledger_operating     NUMERIC(19, 2) NOT NULL,
    ledger_partner       NUMERIC(19, 2) NOT NULL,
    bank_operating       NUMERIC(19, 2),
    bank_partner         NUMERIC(19, 2),
    difference           NUMERIC(19, 2) NOT NULL,
    discrepancies        TEXT,
    triggered_by         VARCHAR(64)    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Single row: outgoing_frozen stops withdrawals and sweeps after a reconciliation mismatch.
CREATE TABLE system_control (
    id              SMALLINT PRIMARY KEY CHECK (id = 1),
    outgoing_frozen BOOLEAN      NOT NULL DEFAULT FALSE,
    frozen_reason   VARCHAR(500),
    frozen_at       TIMESTAMPTZ,
    unfrozen_at     TIMESTAMPTZ,
    unfrozen_by     UUID
);
INSERT INTO system_control (id) VALUES (1);
