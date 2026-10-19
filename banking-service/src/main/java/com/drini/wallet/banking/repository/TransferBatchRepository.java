package com.drini.wallet.banking.repository;

import com.drini.wallet.banking.domain.TransferBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Saves batch runs (when the batch ran and what it sent). */
public interface TransferBatchRepository extends JpaRepository<TransferBatch, UUID> {
}
