package com.drini.wallet.banking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One run of the batch job (table "transfer_batches"): when it ran and what it did. */
@Entity
@Table(name = "transfer_batches")
@Getter
@Setter
public class TransferBatch {

    @Id
    private UUID id;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "sent_count")
    private int sentCount;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "rejected_count")
    private int rejectedCount;

    @Column(name = "skipped_frozen_count")
    private int skippedFrozenCount;

    public TransferBatch() {
    }

    public TransferBatch(String triggerType) {
        this.id = UUID.randomUUID();
        this.triggerType = triggerType;
        this.startedAt = Instant.now();
    }
}
