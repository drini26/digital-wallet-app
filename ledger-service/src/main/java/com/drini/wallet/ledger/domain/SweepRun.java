package com.drini.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One end-of-day liquidity sweep (table "sweep_runs"): how much moved to the partner bank and whether it worked. */
@Entity
@Table(name = "sweep_runs")
@Getter
@Setter
public class SweepRun {

    @Id
    private UUID id;

    private BigDecimal amount;

    @Column(name = "reserve_limit")
    private BigDecimal reserveLimit;

    private String status;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public SweepRun() {
    }
}
