-- banking-service schema: bank transfers, batches, and the mock partner bank (demo only).

-- Bank transfers: REQUESTED -> QUEUED -> SENT -> SETTLED, or PENDING_RETRY, REJECTED, FAILED.
CREATE TABLE bank_transfers (
    id                      UUID PRIMARY KEY,
    owner_id                UUID           NOT NULL,
    wallet_account_id       UUID           NOT NULL,
    direction               VARCHAR(8)     NOT NULL CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    amount                  NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency                VARCHAR(3)     NOT NULL,
    routing_number          VARCHAR(9)     NOT NULL,
    external_account_number VARCHAR(17)    NOT NULL,
    status                  VARCHAR(16)    NOT NULL CHECK (status IN ('REQUESTED', 'QUEUED', 'SENT', 'PENDING_RETRY',
                                                                      'SETTLED', 'REJECTED', 'FAILED')),
    batch_id                UUID,
    -- Retries always send the same reference, so the bank never pays twice.
    bank_reference          VARCHAR(64)    NOT NULL,
    attempt_count           INT            NOT NULL DEFAULT 0,
    next_attempt_at         TIMESTAMPTZ,
    last_error              VARCHAR(300),
    estimated_arrival       DATE,
    -- false until the ledger has the final result; a job retries (outbox).
    ledger_notified         BOOLEAN        NOT NULL DEFAULT FALSE,
    idempotency_key         VARCHAR(100)   NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_bank_transfers_reference UNIQUE (bank_reference),
    -- One transfer per user and Idempotency-Key.
    CONSTRAINT ux_bank_transfers_idempotency UNIQUE (owner_id, idempotency_key)
);
CREATE INDEX ix_bank_transfers_status ON bank_transfers (status, next_attempt_at);
CREATE INDEX ix_bank_transfers_owner ON bank_transfers (owner_id, created_at DESC);

-- Batch runs (10:00, 16:00 or manual).
CREATE TABLE transfer_batches (
    id                   UUID PRIMARY KEY,
    trigger_type         VARCHAR(16) NOT NULL CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    started_at           TIMESTAMPTZ NOT NULL,
    finished_at          TIMESTAMPTZ,
    sent_count           INT         NOT NULL DEFAULT 0,
    retry_count          INT         NOT NULL DEFAULT 0,
    rejected_count       INT         NOT NULL DEFAULT 0,
    skipped_frozen_count INT         NOT NULL DEFAULT 0
);

-- Mock partner bank (demo only; in production this is the bank's API).

-- Bank mode, switchable from the admin page: NORMAL, OFFLINE, REJECT_NEXT, ACK_LOST.
CREATE TABLE mock_bank_settings (
    id                         SMALLINT PRIMARY KEY CHECK (id = 1),
    mode                       VARCHAR(16) NOT NULL CHECK (mode IN ('NORMAL', 'OFFLINE', 'REJECT_NEXT', 'ACK_LOST')),
    settlement_delay_seconds   INT         NOT NULL DEFAULT 10
);
INSERT INTO mock_bank_settings (id, mode, settlement_delay_seconds) VALUES (1, 'NORMAL', 10);

-- Our two accounts at the bank: OPERATING and PARTNER.
CREATE TABLE mock_bank_accounts (
    code    VARCHAR(16) PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0
);
INSERT INTO mock_bank_accounts (code, balance) VALUES ('OPERATING', 0), ('PARTNER', 0);

-- Transfers the bank received; the reference as primary key deduplicates repeats.
CREATE TABLE mock_bank_received (
    bank_reference VARCHAR(64) PRIMARY KEY,
    direction      VARCHAR(8)     NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    status         VARCHAR(16)    NOT NULL CHECK (status IN ('ACCEPTED', 'SETTLED', 'REJECTED')),
    received_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    settle_after   TIMESTAMPTZ    NOT NULL,
    settled_at     TIMESTAMPTZ
);

-- Balance movements; the same sweep or settlement moves money only once.
CREATE TABLE mock_bank_movements (
    id           BIGSERIAL PRIMARY KEY,
    account_code VARCHAR(16)    NOT NULL REFERENCES mock_bank_accounts (code),
    amount       NUMERIC(19, 2) NOT NULL,
    reason       VARCHAR(32)    NOT NULL,
    reference    VARCHAR(64)    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_mock_bank_movement UNIQUE (reference, account_code)
);
