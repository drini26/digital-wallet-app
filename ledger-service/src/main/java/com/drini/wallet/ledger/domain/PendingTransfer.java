package com.drini.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A bank transfer that is on its way (table "pending_transfers"), shown as "pending" on the dashboard. */
@Entity
@Table(name = "pending_transfers")
@Getter
@Setter
public class PendingTransfer {

    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "wallet_account_id")
    private UUID walletAccountId;

    private String direction;

    private BigDecimal amount;

    private String status;

    @Column(name = "estimated_arrival")
    private LocalDate estimatedArrival;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PendingTransfer() {
    }

    public PendingTransfer(UUID transferId, UUID walletAccountId, String direction, BigDecimal amount,
                           LocalDate estimatedArrival) {
        this.transferId = transferId;
        this.walletAccountId = walletAccountId;
        this.direction = direction;
        this.amount = amount;
        this.status = "PENDING";
        this.estimatedArrival = estimatedArrival;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void finish(String finalStatus) {
        this.status = finalStatus;
        this.updatedAt = Instant.now();
    }
}
