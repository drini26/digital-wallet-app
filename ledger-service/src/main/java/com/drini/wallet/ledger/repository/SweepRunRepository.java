package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.SweepRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Saves end-of-day sweep runs (history of what moved to the partner bank). */
public interface SweepRunRepository extends JpaRepository<SweepRun, UUID> {
}
