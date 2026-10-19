package com.drini.wallet.ledger.domain;

import com.drini.wallet.common.money.Money;
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
import java.util.UUID;

/**
 * One business event that moves money (table "ledger_transactions"): a payment, a deposit, a withdrawal step, a sweep
 * or a reversal.
 */
@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
public class LedgerTransaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @Column(name = "payer_account_id")
    private UUID payerAccountId;

    @Column(name = "payee_account_id")
    private UUID payeeAccountId;

    private BigDecimal amount;

    private String currency;

    @Column(name = "resulting_payer_balance")
    private BigDecimal resultingPayerBalance;

    private String description;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "created_at")
    private Instant createdAt;

    public LedgerTransaction() {
    }

    public LedgerTransaction(TransactionType type, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.status = "SUCCESS";
        this.amount = amount;
        this.currency = Money.EUR;
        this.createdAt = Instant.now();
    }
}
