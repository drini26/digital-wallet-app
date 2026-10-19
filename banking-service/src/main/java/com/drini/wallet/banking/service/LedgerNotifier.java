package com.drini.wallet.banking.service;

import com.drini.wallet.banking.client.LedgerClient;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferStatus;
import com.drini.wallet.banking.repository.BankTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Tells the ledger the final result of a transfer and keeps retrying until the ledger has heard it (outbox). */
@Service
public class LedgerNotifier {

    private static final Logger log = LoggerFactory.getLogger(LedgerNotifier.class);
    private static final List<TransferStatus> FINAL_STATUSES = List.of(TransferStatus.SETTLED, TransferStatus.REJECTED);

    private final BankTransferRepository transfers;
    private final LedgerClient ledger;

    public LedgerNotifier(BankTransferRepository transfers, LedgerClient ledger) {
        this.transfers = transfers;
        this.ledger = ledger;
    }

    public void notifyLedger(BankTransfer transfer) {
        try {
            if (transfer.getStatus() == TransferStatus.SETTLED) {
                ledger.settled(transfer);
            } else if (transfer.getStatus() == TransferStatus.REJECTED) {
                ledger.failed(transfer);
            } else {
                return;
            }
            transfer.setLedgerNotified(true);
            transfers.save(transfer);
        } catch (RuntimeException e) {
            log.warn("Ledger not told yet about transfer {} ({}): {}", transfer.getId(), transfer.getStatus(),
                    e.getMessage());
        }
    }

    public void retryPendingNotifications() {
        for (BankTransfer transfer : transfers.findByLedgerNotifiedFalseAndStatusIn(FINAL_STATUSES)) {
            notifyLedger(transfer);
        }
    }

    public long countPendingNotifications() {
        return transfers.countByLedgerNotifiedFalseAndStatusIn(FINAL_STATUSES);
    }
}
