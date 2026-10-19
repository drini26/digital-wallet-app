package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Reads and saves ledger transactions. */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);

    Optional<LedgerTransaction> findByTypeAndExternalReference(TransactionType type, String externalReference);

    Optional<LedgerTransaction> findByReversalOf(UUID originalTransactionId);
}
