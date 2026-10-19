package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.SystemControl;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and saves the single system_control row (outgoing money frozen or not). */
public interface SystemControlRepository extends JpaRepository<SystemControl, Short> {
}
