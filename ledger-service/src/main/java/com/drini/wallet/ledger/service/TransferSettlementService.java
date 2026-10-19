package com.drini.wallet.ledger.service;

import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.api.dto.TransferNotice;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PendingTransfer;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.LedgerTransactionRepository;
import com.drini.wallet.ledger.repository.PendingTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Records what happened to a bank transfer, as reported by banking-service (incoming, settled, failed). */
@Service
public class TransferSettlementService {

    private final LedgerTransactionRepository transactions;
    private final PendingTransferRepository pendingTransfers;
    private final LedgerPostingService posting;
    private final DepositService deposits;

    public TransferSettlementService(LedgerTransactionRepository transactions, PendingTransferRepository pendingTransfers,
                                     LedgerPostingService posting, DepositService deposits) {
        this.transactions = transactions;
        this.pendingTransfers = pendingTransfers;
        this.posting = posting;
        this.deposits = deposits;
    }

    @Transactional
    public void incoming(UUID transferId, TransferNotice notice) {
        if (pendingTransfers.existsById(transferId)) {
            return;
        }
        BigDecimal amount = Money.requirePositiveAmount(notice.getAmount());
        pendingTransfers.save(new PendingTransfer(transferId, notice.getWalletId(), "INFLOW", amount,
                notice.getEstimatedArrival()));
    }

    @Transactional
    public void settled(UUID transferId, TransferNotice notice) {
        BigDecimal amount = Money.requirePositiveAmount(notice.getAmount());
        String reference = transferId.toString();

        if ("OUTFLOW".equals(notice.getDirection())) {
            if (alreadyPosted(TransactionType.WITHDRAWAL_SETTLED, reference)) {
                return;
            }
            Map<UUID, Account> locked = posting.lockInIdOrder(
                    List.of(SystemAccounts.OUTGOING_PENDING, SystemAccounts.OPERATING_BANK));
            LedgerTransaction settlement = new LedgerTransaction(TransactionType.WITHDRAWAL_SETTLED, amount);
            settlement.setExternalReference(reference);
            settlement.setPayerAccountId(notice.getWalletId());
            settlement.setDescription("Withdrawal paid out by the bank");
            posting.post(settlement, List.of(
                    PostingLine.debit(SystemAccounts.OUTGOING_PENDING, amount),
                    PostingLine.credit(SystemAccounts.OPERATING_BANK, amount)), locked);
        } else {
            deposits.deposit(notice.getWalletId(), amount, reference, "Deposit from bank");
        }
        finishPending(transferId, "SETTLED");
    }

    @Transactional
    public void failed(UUID transferId, TransferNotice notice) {
        BigDecimal amount = Money.requirePositiveAmount(notice.getAmount());
        String reference = transferId.toString();

        boolean wasHeld = alreadyPosted(TransactionType.WITHDRAWAL_HOLD, reference);
        if ("OUTFLOW".equals(notice.getDirection()) && wasHeld
                && !alreadyPosted(TransactionType.WITHDRAWAL_RELEASED, reference)) {
            UUID walletId = notice.getWalletId();
            Map<UUID, Account> locked = posting.lockInIdOrder(List.of(walletId, SystemAccounts.OUTGOING_PENDING));
            LedgerTransaction release = new LedgerTransaction(TransactionType.WITHDRAWAL_RELEASED, amount);
            release.setExternalReference(reference);
            release.setPayeeAccountId(walletId);
            release.setDescription("Withdrawal rejected by the bank: money returned");
            posting.post(release, List.of(
                    PostingLine.debit(SystemAccounts.OUTGOING_PENDING, amount),
                    PostingLine.credit(walletId, amount)), locked);
        }
        finishPending(transferId, "FAILED");
    }

    private boolean alreadyPosted(TransactionType type, String reference) {
        Optional<LedgerTransaction> existing = transactions.findByTypeAndExternalReference(type, reference);
        return existing.isPresent();
    }

    private void finishPending(UUID transferId, String finalStatus) {
        Optional<PendingTransfer> pending = pendingTransfers.findById(transferId);
        if (pending.isPresent() && "PENDING".equals(pending.get().getStatus())) {
            pending.get().finish(finalStatus);
        }
    }
}
