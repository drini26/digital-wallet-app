package com.drini.wallet.banking.service;

import com.drini.wallet.banking.api.dto.BatchResultResponse;
import com.drini.wallet.banking.client.LedgerClient;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferBatch;
import com.drini.wallet.banking.domain.TransferStatus;
import com.drini.wallet.banking.mockbank.BankUnavailableException;
import com.drini.wallet.banking.mockbank.MockPartnerBank;
import com.drini.wallet.banking.repository.BankTransferRepository;
import com.drini.wallet.banking.repository.TransferBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Sends queued transfers to the bank in batches and resends the ones the bank could not receive. */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);
    static final Duration FIRST_RETRY_WAIT = Duration.ofSeconds(30);
    static final Duration MAX_RETRY_WAIT = Duration.ofMinutes(30);

    private final BankTransferRepository transfers;
    private final TransferBatchRepository batches;
    private final MockPartnerBank bank;
    private final LedgerClient ledger;
    private final LedgerNotifier ledgerNotifier;

    public BatchService(BankTransferRepository transfers, TransferBatchRepository batches, MockPartnerBank bank,
                        LedgerClient ledger, LedgerNotifier ledgerNotifier) {
        this.transfers = transfers;
        this.batches = batches;
        this.bank = bank;
        this.ledger = ledger;
        this.ledgerNotifier = ledgerNotifier;
    }

    public synchronized BatchResultResponse runBatch(String triggerType) {
        boolean frozen = isOutgoingFrozen();
        TransferBatch batch = batches.save(new TransferBatch(triggerType));

        List<BankTransfer> queued = transfers.findByStatusOrderByCreatedAtAsc(TransferStatus.QUEUED);
        for (BankTransfer transfer : queued) {
            if (transfer.isOutflow() && frozen) {
                batch.setSkippedFrozenCount(batch.getSkippedFrozenCount() + 1);
                continue;
            }
            TransferStatus result = sendToBank(transfer, batch);
            countResult(batch, result);
        }

        batch.setFinishedAt(Instant.now());
        batches.save(batch);
        log.info("Batch {} ({}): sent {}, retrying {}, rejected {}, skipped (frozen) {}", batch.getId(), triggerType,
                batch.getSentCount(), batch.getRetryCount(), batch.getRejectedCount(), batch.getSkippedFrozenCount());
        return toResponse(batch);
    }

    /** Resends transfers whose retry time has come, always with the same bank reference. */
    public synchronized int retryDue() {
        List<BankTransfer> due = transfers.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                TransferStatus.PENDING_RETRY, Instant.now());
        if (due.isEmpty()) {
            return 0;
        }
        boolean frozen = isOutgoingFrozen();
        TransferBatch batch = new TransferBatch("SCHEDULED");
        for (BankTransfer transfer : due) {
            if (transfer.isOutflow() && frozen) {
                continue;
            }
            countResult(batch, sendToBank(transfer, batch));
        }
        batch.setFinishedAt(Instant.now());
        batches.save(batch);
        return batch.getSentCount();
    }

    private TransferStatus sendToBank(BankTransfer transfer, TransferBatch batch) {
        transfer.setAttemptCount(transfer.getAttemptCount() + 1);
        try {
            String answer = bank.submit(transfer.getBankReference(), transfer.getDirection().name(), transfer.getAmount());
            if ("REJECTED".equals(answer)) {
                transfer.markRejected("Rejected by the bank");
                transfers.save(transfer);
                ledgerNotifier.notifyLedger(transfer);
                return TransferStatus.REJECTED;
            }
            transfer.markSent(batch.getId());
        } catch (BankUnavailableException e) {
            transfer.markPendingRetry(Instant.now().plus(retryWait(transfer.getAttemptCount())), e.getMessage());
        }
        transfers.save(transfer);
        return transfer.getStatus();
    }

    static Duration retryWait(int attempts) {
        Duration wait = FIRST_RETRY_WAIT;
        for (int i = 1; i < attempts && wait.compareTo(MAX_RETRY_WAIT) < 0; i++) {
            wait = wait.multipliedBy(2);
        }
        if (wait.compareTo(MAX_RETRY_WAIT) > 0) {
            return MAX_RETRY_WAIT;
        }
        return wait;
    }

    private boolean isOutgoingFrozen() {
        try {
            return ledger.isOutgoingFrozen();
        } catch (RuntimeException e) {
            log.warn("Could not ask the ledger whether outgoing money is frozen; withdrawals wait: {}", e.getMessage());
            return true;
        }
    }

    private static void countResult(TransferBatch batch, TransferStatus result) {
        if (result == TransferStatus.SENT) {
            batch.setSentCount(batch.getSentCount() + 1);
        } else if (result == TransferStatus.PENDING_RETRY) {
            batch.setRetryCount(batch.getRetryCount() + 1);
        } else if (result == TransferStatus.REJECTED) {
            batch.setRejectedCount(batch.getRejectedCount() + 1);
        }
    }

    private static BatchResultResponse toResponse(TransferBatch batch) {
        BatchResultResponse response = new BatchResultResponse();
        response.setBatchId(batch.getId());
        response.setSent(batch.getSentCount());
        response.setRetrying(batch.getRetryCount());
        response.setRejected(batch.getRejectedCount());
        response.setSkippedFrozen(batch.getSkippedFrozenCount());
        return response;
    }
}
