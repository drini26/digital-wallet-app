package com.drini.wallet.banking.service;

import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferStatus;
import com.drini.wallet.banking.mockbank.MockPartnerBank;
import com.drini.wallet.banking.repository.BankTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Finishes the transfers the bank has settled: status SETTLED, then the ledger books the money. */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final MockPartnerBank bank;
    private final BankTransferRepository transfers;
    private final LedgerNotifier ledgerNotifier;

    public SettlementService(MockPartnerBank bank, BankTransferRepository transfers, LedgerNotifier ledgerNotifier) {
        this.bank = bank;
        this.transfers = transfers;
        this.ledgerNotifier = ledgerNotifier;
    }

    public int settleDueTransfers(boolean ignoreDelay) {
        List<String> settledReferences = bank.settleDue(ignoreDelay);

        for (String reference : settledReferences) {
            Optional<BankTransfer> found = transfers.findByBankReference(reference);
            if (found.isEmpty()) {
                log.warn("The bank settled unknown reference {}", reference);
                continue;
            }
            BankTransfer transfer = found.get();
            if (transfer.getStatus() == TransferStatus.SETTLED) {
                continue;
            }
            // Saved before calling the ledger, so the retry job can finish it if that call fails.
            transfer.markSettled();
            transfers.save(transfer);

            ledgerNotifier.notifyLedger(transfer);
        }
        return settledReferences.size();
    }
}
