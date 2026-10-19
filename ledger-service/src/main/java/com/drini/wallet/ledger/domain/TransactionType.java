package com.drini.wallet.ledger.domain;

/** What a ledger transaction records. */
public enum TransactionType {
    DEPOSIT,
    PAYMENT,
    WITHDRAWAL_HOLD,
    WITHDRAWAL_SETTLED,
    WITHDRAWAL_RELEASED,
    SWEEP,
    REVERSAL
}
