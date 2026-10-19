package com.drini.wallet.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A transfer between a wallet and an external bank account (table "bank_transfers"). */
@Entity
@Table(name = "bank_transfers")
@Getter
@Setter
public class BankTransfer {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "wallet_account_id")
    private UUID walletAccountId;

    @Enumerated(EnumType.STRING)
    private TransferDirection direction;

    private BigDecimal amount;

    private String currency;

    @Column(name = "routing_number")
    private String routingNumber;

    @Column(name = "external_account_number")
    private String externalAccountNumber;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "estimated_arrival")
    private LocalDate estimatedArrival;

    @Column(name = "ledger_notified")
    private boolean ledgerNotified;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public BankTransfer() {
    }

    public BankTransfer(UUID ownerId, UUID walletAccountId, TransferDirection direction, BigDecimal amount,
                        String routingNumber, String externalAccountNumber, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.walletAccountId = walletAccountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = "EUR";
        this.routingNumber = routingNumber;
        this.externalAccountNumber = externalAccountNumber;
        this.status = TransferStatus.REQUESTED;
        this.bankReference = id.toString();
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public boolean isOutflow() {
        return direction == TransferDirection.OUTFLOW;
    }

    public void markQueued(LocalDate estimatedArrival) {
        requireStatus(TransferStatus.REQUESTED);
        this.estimatedArrival = estimatedArrival;
        changeStatus(TransferStatus.QUEUED);
    }

    public void markFailed(String reason) {
        requireStatus(TransferStatus.REQUESTED);
        this.lastError = reason;
        changeStatus(TransferStatus.FAILED);
    }

    public void markSent(UUID batchId) {
        requireStatus(TransferStatus.QUEUED, TransferStatus.PENDING_RETRY);
        this.batchId = batchId;
        this.nextAttemptAt = null;
        changeStatus(TransferStatus.SENT);
    }

    public void markPendingRetry(Instant nextAttemptAt, String error) {
        requireStatus(TransferStatus.QUEUED, TransferStatus.PENDING_RETRY);
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
        changeStatus(TransferStatus.PENDING_RETRY);
    }

    public void markSettled() {
        requireStatus(TransferStatus.SENT, TransferStatus.PENDING_RETRY);
        this.ledgerNotified = false;
        this.nextAttemptAt = null;
        changeStatus(TransferStatus.SETTLED);
    }

    public void markRejected(String reason) {
        requireStatus(TransferStatus.QUEUED, TransferStatus.PENDING_RETRY, TransferStatus.SENT);
        this.lastError = reason;
        this.ledgerNotified = false;
        this.nextAttemptAt = null;
        changeStatus(TransferStatus.REJECTED);
    }

    private void requireStatus(TransferStatus... allowed) {
        for (TransferStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Transfer " + id + " cannot change from " + status);
    }

    private void changeStatus(TransferStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
