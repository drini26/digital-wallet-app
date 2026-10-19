package com.drini.wallet.banking.repository;

import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and saves bank transfers. */
public interface BankTransferRepository extends JpaRepository<BankTransfer, UUID> {

    Optional<BankTransfer> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    Optional<BankTransfer> findByBankReference(String bankReference);

    List<BankTransfer> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<BankTransfer> findByStatusOrderByCreatedAtAsc(TransferStatus status);

    List<BankTransfer> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(TransferStatus status,
                                                                                        Instant now);

    List<BankTransfer> findByLedgerNotifiedFalseAndStatusIn(Collection<TransferStatus> statuses);

    long countByLedgerNotifiedFalseAndStatusIn(Collection<TransferStatus> statuses);
}
