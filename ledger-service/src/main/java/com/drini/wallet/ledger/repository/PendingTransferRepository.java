package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.PendingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

/** Reads and saves bank transfers that are still on their way (the "pending" amounts of the balance). */
public interface PendingTransferRepository extends JpaRepository<PendingTransfer, UUID> {

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PendingTransfer p "
            + "WHERE p.walletAccountId = :walletId AND p.direction = :direction AND p.status = 'PENDING'")
    BigDecimal sumPending(@Param("walletId") UUID walletId, @Param("direction") String direction);

    long countByStatus(String status);
}
