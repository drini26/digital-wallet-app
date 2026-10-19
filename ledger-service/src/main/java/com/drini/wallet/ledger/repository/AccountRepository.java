package com.drini.wallet.ledger.repository;

import com.drini.wallet.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Reads accounts and creates wallets. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Only the id: the balance is read later, under the row lock. */
    @Query("SELECT a.id FROM Account a WHERE a.ownerId = :ownerId")
    Optional<UUID> findWalletIdByOwnerId(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query(value = "INSERT INTO accounts (id, owner_id, type, display_name, currency, balance, status) "
            + "VALUES (:id, :ownerId, :type, :displayName, 'EUR', 0, 'ACTIVE') "
            + "ON CONFLICT (owner_id) WHERE owner_id IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertWalletIfMissing(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("type") String type,
                              @Param("displayName") String displayName);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.ownerId IS NOT NULL")
    BigDecimal sumOfAllWallets();

    @Query("SELECT COUNT(a) FROM Account a WHERE a.ownerId IS NOT NULL")
    long countWallets();
}
