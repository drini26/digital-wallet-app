package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Saves and lists reconciliation results. */
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    List<ReconciliationRun> findTop10ByOrderByCreatedAtDesc();
}
