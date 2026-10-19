package com.drini.wallet.banking.domain;

/** Where a bank transfer is in its life (see BankTransfer for the allowed moves). */
public enum TransferStatus {
    REQUESTED,
    QUEUED,
    SENT,
    PENDING_RETRY,
    SETTLED,
    REJECTED,
    FAILED
}
