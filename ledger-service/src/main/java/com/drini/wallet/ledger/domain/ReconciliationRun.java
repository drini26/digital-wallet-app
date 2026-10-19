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

/** The result of one reconciliation (table "reconciliation_runs"): the totals compared and whether they matched. */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
public class ReconciliationRun {

    @Id
    private UUID id;

    private String status;

    @Column(name = "wallets_total")
    private BigDecimal walletsTotal;

    @Column(name = "ledger_operating")
    private BigDecimal ledgerOperating;

    @Column(name = "ledger_partner")
    private BigDecimal ledgerPartner;

    @Column(name = "bank_operating")
    private BigDecimal bankOperating;

    @Column(name = "bank_partner")
    private BigDecimal bankPartner;

    private BigDecimal difference;

    private String discrepancies;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "outgoing_pending")
    private BigDecimal outgoingPending;

    public ReconciliationRun() {
    }
}
